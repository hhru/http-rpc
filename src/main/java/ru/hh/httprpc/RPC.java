package ru.hh.httprpc;

public class RPC<I, O> {
  public final String path;
  public final Class<I> inputClass;
  public final Class<O> outputClass;

  public static <I, O> RPC<I, O> signature(String path, Class<I> inputClass, Class<O> outputClass) {
    return new RPC<I, O>(path, inputClass, outputClass);
  }

  private RPC(String path, Class<I> inputClass, Class<O> outputClass) {
    this.path = path;
    this.inputClass = inputClass;
    this.outputClass = outputClass;
  }
}
