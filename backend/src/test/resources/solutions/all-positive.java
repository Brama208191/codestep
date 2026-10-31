class Solution { public boolean allPositive(int[] a) { for (int x : a) if (x <= 0) return false; return true; } }
