class Solution { public int countTarget(int[] a, int t) { int c = 0; for (int x : a) if (x == t) c++; return c; } }
