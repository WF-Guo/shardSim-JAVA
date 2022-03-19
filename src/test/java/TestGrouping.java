public class TestGrouping {
    public static void main(String[] args) {
        int groupNumber = 5;
        int size = 34;
        int start = 0;
        for (int i = 0; i < groupNumber; i++) {
            int cnt = 0;
            int j;
            for (j = start; j * groupNumber < (i+1) * size && j < size; j++)
                cnt++;
            System.out.println(cnt);
            start = j;
        }
    }
}
