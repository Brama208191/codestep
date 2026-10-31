class Solution { public int countEvens(int[] a) { int c = 0; for (int x : a) if (x % 2 == 0) c++; return c; } }
