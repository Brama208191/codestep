class Solution { public int[] reverse(int[] a) { int[] r = new int[a.length]; for (int i = 0; i < a.length; i++) r[i] = a[a.length - 1 - i]; return r; } }
