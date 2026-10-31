class Solution { public int countVowels(String s) { return s.replaceAll("[^aeiouAEIOU]", "").length(); } }
