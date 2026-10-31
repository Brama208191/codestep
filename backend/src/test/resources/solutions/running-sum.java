class Solution { public int[] runningSum(int[] a) { int[] r = a.clone(); for (int i = 1; i < r.length; i++) r[i] += r[i-1]; return r; } }
