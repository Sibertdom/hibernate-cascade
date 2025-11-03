package core.basesyntax;

import core.basesyntax.dao.impl.*;
import core.basesyntax.model.*;
import core.basesyntax.HibernateUtil;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

public class Main {
    // Ініціалізація DAO (використовуємо повну назву HibernateUtil)
    private static final UserDaoImpl userDao = new UserDaoImpl(core.basesyntax.HibernateUtil.getSessionFactory());
    private static final CommentDaoImpl commentDao = new CommentDaoImpl(core.basesyntax.HibernateUtil.getSessionFactory());
    private static final MessageDaoImpl messageDao = new MessageDaoImpl(core.basesyntax.HibernateUtil.getSessionFactory());
    private static final SmileDaoImpl smileDao = new SmileDaoImpl(core.basesyntax.HibernateUtil.getSessionFactory());
    // Потрібен DAO для деталей для перевірки видалення
    private static final MessageDetailsDaoImpl messageDetailsDao = new MessageDetailsDaoImpl(core.basesyntax.HibernateUtil.getSessionFactory());


    public static void main(String[] args) {
        System.out.println("--- Запуск тестування Hibernate Cascades ---");

        // 1. Тест User -> Comment (CascadeType.PERSIST / NO REMOVE)
        testUserCommentCascade();

        // 2. Тест Comment -> Smile (NO PERSIST / NO REMOVE)
        testCommentSmileCascade();

        // 3. Тест Message -> MessageDetails (CascadeType.ALL)
        testMessageDetailsCascade();

        System.out.println("--- Тестування завершено. Перевірте виведення SQL та стан БД ---");
    }

    // --- Сценарій 1: User та Comment ---
    private static void testUserCommentCascade() {
        System.out.println("\n--- Сценарій 1: User та Comment (PERSIST, NO REMOVE) ---");

        // Створення нового User з новими Comment (PERSIST має спрацювати)
        User newUser = new User();
        newUser.setUsername("JohnDoe");

        Comment c1 = new Comment();
        c1.setText("Чудовий пост!");
        c1.setUser(newUser);

        Comment c2 = new Comment();
        c2.setText("Дякую за інформацію.");
        c2.setUser(newUser);

        // Встановлення двонаправленого зв'язку User -> Comment
        newUser.getComments().add(c1);
        newUser.getComments().add(c2);

        userDao.create(newUser);
        System.out.println("1. Створено User та 2 Comment (через PERSIST). User ID: " + newUser.getId());
        System.out.println("   Comment IDs: " + c1.getId() + ", " + c2.getId());

        // Видалення User (NO REMOVE має спрацювати, коментарі залишаються)
        userDao.remove(newUser);
        System.out.println("2. Видалено User. Перевірка коментарів...");

        // Перевірка, що коментарі існують, але поле user_id = NULL
        Comment foundC1 = commentDao.get(c1.getId());
        Comment foundC2 = commentDao.get(c2.getId());

        // Перевірка, чи не NULL, перш ніж звертатися до getUser()
        String c1User = foundC1 != null && foundC1.getUser() == null ? "NULL (OK)" : "N/A";
        String c2User = foundC2 != null && foundC2.getUser() == null ? "NULL (OK)" : "N/A";

        System.out.println("   C1 існує? " + (foundC1 != null) + ". User: " + c1User);
        System.out.println("   C2 існує? " + (foundC2 != null) + ". User: " + c2User);
    }

    // --- Сценарій 2: Comment та Smile ---
    private static void testCommentSmileCascade() {
        System.out.println("\n--- Сценарій 2: Comment та Smile (MERGE, NO PERSIST, NO REMOVE) ---");

        // 1. Створення існуючого Smile
        Smile existingSmile = new Smile("😊");
        smileDao.create(existingSmile);
        System.out.println("1. Створено Smile: " + existingSmile.getValue() + " (ID: " + existingSmile.getId() + ")");

        // 2. Створення Comment з існуючим Smile
        Comment newComment = new Comment();
        newComment.setText("Коментар зі смайлом.");
        // Важливо: У моделі Comment.java має бути Set<Smile>
        newComment.setSmiles(new HashSet<>(List.of(existingSmile)));

        commentDao.create(newComment);
        System.out.println("2. Створено Comment: " + newComment.getId() + " з існуючим Smile.");

        // 3. Видалення Comment (Smile має залишитись)
        commentDao.remove(newComment);
        System.out.println("3. Видалено Comment. Перевірка Smile...");

        // Перевірка, що Smile залишився
        Smile foundSmile = smileDao.get(existingSmile.getId());
        System.out.println("   Smile існує? " + (foundSmile != null) + ". Value: " + (foundSmile != null ? foundSmile.getValue() : "N/A"));

        // Додаткове очищення (видалення Smile, щоб не засмічувати БД)
        if (foundSmile != null) {
            smileDao.remove(foundSmile);
        }
    }

    // --- Сценарій 3: Message та MessageDetails ---
    private static void testMessageDetailsCascade() {
        System.out.println("\n--- Сценарій 3: Message та MessageDetails (CascadeType.ALL) ---");

        // 1. Створення Message з новим MessageDetails (PERSIST має спрацювати)
        MessageDetails details = new MessageDetails();
        details.setSender("Alice");
        details.setSentTime(LocalDateTime.now());

        Message message = new Message();
        message.setContent("Важливе повідомлення.");

        // !!! ВИПРАВЛЕННЯ: Встановлюємо зв'язок на обох сторонах,
        // якщо це не зроблено у сеттері Message.setMessageDetails().
        // Якщо у Message.java є допоміжний метод синхронізації, це спрощується.
        message.setMessageDetails(details);
        details.setMessage(message); // <-- Встановлення зворотного зв'язку! (Критично для One-to-One)

        messageDao.create(message);
        Long detailsId = details.getId();
        System.out.println("1. Створено Message та Details (через ALL). Message ID: " + message.getId() + ", Details ID: " + detailsId);

        // 2. Видалення Message (REMOVE має спрацювати, Details видаляються)
        messageDao.remove(message);
        System.out.println("2. Видалено Message. Перевірка Details...");

        // Перевірка, що Details було видалено
        MessageDetails foundDetails = messageDetailsDao.get(detailsId);
        System.out.println("   MessageDetails існує? " + (foundDetails != null) + (foundDetails == null ? " (OK)" : " (FAIL)"));
    }
}
