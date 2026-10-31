import java.util.*; class Solution { public boolean hasDuplicate(int[] a) { Set<Integer> s = new HashSet<>(); for (int x : a) if (!s.add(x)) return true; return false; } }
