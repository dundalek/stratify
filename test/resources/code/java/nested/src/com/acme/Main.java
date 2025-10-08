package com.acme;

import com.acme.greeting.Greeter;
import com.acme.greeting.Printer;

public class Main {
  public static void main(String[] args) {
    Printer.println(Greeter.greet());
  }
}
