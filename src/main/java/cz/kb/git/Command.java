package cz.kb.git;

@FunctionalInterface
public interface Command <T, R> {

    R execute(T input) throws  Exception;
}
