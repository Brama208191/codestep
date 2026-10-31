import java.io.*;
class Helper { static String longest(String[] w) { String b = w[0]; for (String x : w) if (x.length() > b.length()) b = x; return b; } }
class App {
  public static void main(String[] args) throws Exception {
    BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
    System.out.println(Helper.longest(r.readLine().split(" ")));
  }
}
