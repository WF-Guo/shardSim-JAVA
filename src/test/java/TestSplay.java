import edu.pku.infosec.util.SplayTree;

public class TestSplay {
    public static void main(String[] args) {
        SplayTree tree = new SplayTree();
        tree.pushBack(1);
        tree.pushBack(2);
        tree.pushBack(3);
        tree.pushBack(4);
        tree.pushBack(5);
        tree.pushBack(6);
        System.out.println(tree.removeKth(3));
        System.out.println(tree.removeKth(1));
        System.out.println(tree.removeKth(4));
        tree.pushBack(7);
        tree.pushBack(8);
        System.out.println(tree.size());
        System.out.println(tree.removeKth(5));
        System.out.println(tree.removeKth(1));
        System.out.println(tree.removeKth(1));
        System.out.println(tree.removeKth(1));
        System.out.println(tree.removeKth(1));
        tree.pushBack(9);
        tree.pushBack(10);
        System.out.println(tree.removeKth(2));
        System.out.println(tree.removeKth(1));

    }
}
