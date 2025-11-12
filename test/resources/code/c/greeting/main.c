#include "greeting.h"
#include <stdio.h>

int main(void) {
  printf("%s\n", greet());
  return 0;
}

// gcc -o greeting main.c greeting.c && ./greeting
