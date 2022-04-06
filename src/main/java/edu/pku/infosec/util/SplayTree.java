package edu.pku.infosec.util;

import java.util.Stack;

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
        class LayerData {
            public final TreeNode p;
            public final int dir;

            public LayerData(TreeNode p, int dir) {
                this.p = p;
                this.dir = dir;
            }
        }
        Stack<LayerData> stack = new Stack<>();
        TreeNode currentNode = this;
        int lSize = currentNode.leftSize();
        while (k != lSize + 1) {
            if (k <= lSize) {
                stack.add(new LayerData(currentNode, 0));
                currentNode = currentNode.ch[0];
                lSize = currentNode.leftSize();
            } else {
                k -= lSize + 1;
                stack.add(new LayerData(currentNode, 1));
                currentNode = currentNode.ch[1];
                lSize = currentNode.leftSize();
            }
        }
        while (stack.size() >= 2) {
            final LayerData l2 = stack.pop();
            final LayerData l1 = stack.pop();
            l2.p.ch[l2.dir] = currentNode;
            if (l1.dir == l2.dir)
                currentNode = l1.p.rotate(l1.dir ^ 1).rotate(l2.dir ^ 1);
            else {
                l1.p.ch[l1.dir] = l2.p.rotate(l2.dir ^ 1);
                currentNode = l1.p.rotate(l1.dir ^ 1);
            }
        }
        if (!stack.isEmpty()) {
            final LayerData l = stack.pop();
            l.p.ch[l.dir] = currentNode;
            currentNode = l.p.rotate(l.dir ^ 1);
        }
        return currentNode;
    }
}
