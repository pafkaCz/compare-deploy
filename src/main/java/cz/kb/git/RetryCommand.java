package cz.kb.git;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

@Slf4j
public class RetryCommand<T, R> implements Command<T, R> {

    private static final Random RANDOM = new Random();
    private Command<T, R> command;
    private int maxRetries;
    private AtomicInteger retries;
    private long maxDelay;
    private Predicate<Exception> test;
    private List<Exception> errors;

    RetryCommand(Command<T, R> command) {
        this(1, 25);
        this.command = command;
    }

//    @SafeVarargs
    private RetryCommand(int maxRetries, long maxDelay, Predicate<Exception>... ignoreTests) {
        this.maxRetries = maxRetries;
        this.maxDelay = maxDelay;
        this.retries = new AtomicInteger();
        this.test = Arrays.stream(ignoreTests).reduce(Predicate::or).orElse(e -> true);
        this.errors = new ArrayList<>();
    }

    public List<Exception> getErrors() {
        return errors;
    }

    public int getRetries() {
        return retries.intValue();
    }

    @Override
    public R execute(T input) throws Exception {
        do {
            try {
                return command.execute(input);
            } catch (Exception e) {
                this.errors.add(e);

                if (retries.incrementAndGet() > maxRetries || !this.test.test(e)) {
                    throw e;
                }

                try {
                    var testDelay = (long) Math.pow(2, getRetries()) * 1000 + RANDOM.nextInt(1000);
                    var delay = Math.min(testDelay, maxDelay * 1000);
                    LOG.debug("Retry #{} in {} s", getRetries(), delay / 1000);
                    Thread.sleep(delay);
                } catch (InterruptedException f) {
                    LOG.error("Sleep interrupted", e);
                }
            }
        } while (true);
    }
}
