class Solution { public int findMax(int[] a) { int b = a[0]; for (int x : a) if (x > b) b = x; return b; } }
