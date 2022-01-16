package edu.pku.infosec.util;

public class SplayTree {
    TreeNode root = null;

    public void pushBack(Object data) {
        if (root == null)
            root = new TreeNode(data);
        else {
            root = root.splayUpKthInSubtree(root.subTreeSize);
            if (root.ch[1] != null)
                throw new RuntimeException("splay error");
            root.ch[1] = new TreeNode(data);
            root.maintainSize();
        }
    }

    public Object removeKth(int k) {
        if (k == 1) {
            TreeNode tmp = root.splayUpKthInSubtree(1);
            root = tmp.ch[1];
            return tmp.data;
        } else {
            root = root.splayUpKthInSubtree(k - 1);
            TreeNode tmp = root.ch[1].splayUpKthInSubtree(1);
            root.ch[1] = tmp.ch[1];
            root.maintainSize();
            return tmp.data;
        }
    }

    public int size() {
        return root == null ? 0 : root.subTreeSize;
    }
}

class TreeNode {
    TreeNode[] ch = new TreeNode[2];
    Object data;
    int subTreeSize;

    TreeNode(Object data) {
        // Create an orphan node
        this.data = data;
        ch[0] = ch[1] = null;
        subTreeSize = 1;
    }

    private int leftSize() {
        return ch[0] == null ? 0 : ch[0].subTreeSize;
    }

    void maintainSize() {
        int sum = 1;
        if (ch[0] != null)
            sum += ch[0].subTreeSize;
        if (ch[1] != null)
            sum += ch[1].subTreeSize;
        this.subTreeSize = sum;
    }

    /**
     * @param dir 0:left, 1:right
     * @return new node at this place
     */
    TreeNode rotate(int dir) {
        TreeNode p = this.ch[dir ^ 1];
        this.ch[dir ^ 1] = p.ch[dir];
        p.ch[dir] = this;
        this.maintainSize();
        p.maintainSize();
        return p;
    }

    TreeNode splayUpKthInSubtree(int k) {
        int lSize = this.leftSize();
        int d1;
        if (k <= lSize)
            d1 = 0;
        else if (k == lSize + 1)
            return this;
        else {
            k -= lSize + 1;
            d1 = 1;
        }
        TreeNode p = ch[d1];
        lSize = p.leftSize();
        int d2;
        if (k <= lSize)
            d2 = 0;
        else if (k == lSize + 1) {
            return rotate(d1 ^ 1);
        } else {
            k -= lSize + 1;
            d2 = 1;
        }
        p.ch[d2] = p.ch[d2].splayUpKthInSubtree(k);
        if (d1 == d2)
            return rotate(d1 ^ 1).rotate(d2 ^ 1);
        else {
            ch[d1] = ch[d1].rotate(d2 ^ 1);
            return rotate(d1 ^ 1);
        }
    }
}
