#include "greeting.hpp"
#include <iostream>

int main() {
  std::cout << greet() << std::endl;
  return 0;
}

// g++ -o greeting main.cpp greeting.cpp && ./greeting
