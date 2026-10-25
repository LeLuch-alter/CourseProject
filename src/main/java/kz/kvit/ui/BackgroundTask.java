package kz.kvit.ui;

import javafx.concurrent.Task;
import kz.kvit.i18n.I18n;
import kz.kvit.repository.DataAccessException;
import kz.kvit.service.ValidationException;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Runs network work off the JavaFX thread so the window does not freeze,
 * then calls back on the JavaFX thread with the result or a user-readable error.
 */
public final class BackgroundTask {

    private BackgroundTask() {
    }

    public static <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<String> onError) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> onError.accept(userMessage(task.getException())));

        Thread thread = new Thread(task, "background-task");
        thread.setDaemon(true);
        thread.start();
    }

    static String userMessage(Throwable error) {
        if (error instanceof ValidationException || error instanceof DataAccessException) {
            return error.getMessage();
        }
        error.printStackTrace();
        return I18n.t("error.unexpected", error);
    }
}
