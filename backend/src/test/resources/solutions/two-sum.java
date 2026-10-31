import java.util.*;
public class Main {
  public static void main(String[] args) {
    Scanner in = new Scanner(System.in);
    int n = in.nextInt(); int[] a = new int[n];
    for (int i = 0; i < n; i++) a[i] = in.nextInt();
    int t = in.nextInt();
    for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) if (a[i] + a[j] == t) { System.out.println(i + " " + j); return; }
  }
}
