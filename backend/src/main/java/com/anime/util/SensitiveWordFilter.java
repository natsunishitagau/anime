package com.anime.util;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

@Service
public class SensitiveWordFilter {

    private static final char REPLACE_CHAR = '*';
    private static final int TREE_NODE_INIT_SIZE = 128;
    private static final int INITIAL_CHILD_SIZE = 65536;

    private final char[][] tree = new char[TREE_NODE_INIT_SIZE][];
    private final boolean[] isEnd = new boolean[TREE_NODE_INIT_SIZE];
    private int nodeCount = 1;

    @PostConstruct
    public void init() {
        tree[0] = new char[INITIAL_CHILD_SIZE];
        loadSensitiveWords();
    }

    private void loadSensitiveWords() {
        String[] words = {
            "傻逼", "蠢货", "废物", "垃圾", "智障", "白痴", "弱智",
            "滚蛋", "去死", "该死", "王八蛋", "混蛋", "杂种", "畜生",
            "妈的", "操", "艹", "肏", "尻", "屌", "婊", "逼",
            "色情", "赌博", "毒品", "诈骗", "暴力",
            "台独", "港独", "藏独", "疆独",
            "反动",
        };
        for (String word : words) {
            addWord(word);
        }
    }

    public void addWord(String word) {
        if (word == null || word.isEmpty()) {
            return;
        }
        char[] chars = word.toCharArray();
        int nodeIndex = 0;
        for (char c : chars) {
            int charIndex = c;
            if (charIndex >= tree[nodeIndex].length) {
                char[] newChildren = new char[charIndex + 1];
                System.arraycopy(tree[nodeIndex], 0, newChildren, 0, tree[nodeIndex].length);
                tree[nodeIndex] = newChildren;
            }
            if (tree[nodeIndex][charIndex] == 0) {
                tree[nodeIndex][charIndex] = (char) allocateNode();
                tree[nodeIndex] = ensureCapacity(tree[nodeIndex], charIndex + 1);
            }
            nodeIndex = tree[nodeIndex][charIndex];
        }
        isEnd[nodeIndex] = true;
    }

    private int allocateNode() {
        if (nodeCount >= tree.length) {
            char[][] newTree = new char[nodeCount * 2][];
            boolean[] newIsEnd = new boolean[nodeCount * 2];
            System.arraycopy(tree, 0, newTree, 0, nodeCount);
            System.arraycopy(isEnd, 0, newIsEnd, 0, nodeCount);
            tree[nodeCount] = new char[INITIAL_CHILD_SIZE];
            return nodeCount++;
        }
        tree[nodeCount] = new char[INITIAL_CHILD_SIZE];
        return nodeCount++;
    }

    private char[] ensureCapacity(char[] arr, int size) {
        if (size > arr.length) {
            char[] newArr = new char[size * 2];
            System.arraycopy(arr, 0, newArr, 0, arr.length);
            return newArr;
        }
        return arr;
    }

    public boolean containsSensitiveWord(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            int nodeIndex = 0;
            int j = i;
            while (j < chars.length) {
                int charIndex = chars[j];
                if (charIndex >= tree[nodeIndex].length || tree[nodeIndex][charIndex] == 0) {
                    break;
                }
                nodeIndex = tree[nodeIndex][charIndex];
                if (isEnd[nodeIndex]) {
                    return true;
                }
                j++;
            }
        }
        return false;
    }

    public String filter(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        char[] chars = text.toCharArray();
        char[] result = text.toCharArray();
        boolean[] replaced = new boolean[chars.length];

        for (int i = 0; i < chars.length; i++) {
            if (replaced[i]) continue;
            int nodeIndex = 0;
            int matchEnd = -1;

            for (int j = i; j < chars.length; j++) {
                int charIndex = chars[j];
                if (charIndex >= tree[nodeIndex].length || tree[nodeIndex][charIndex] == 0) {
                    break;
                }
                nodeIndex = tree[nodeIndex][charIndex];
                if (isEnd[nodeIndex]) {
                    matchEnd = j;
                }
            }

            if (matchEnd != -1) {
                for (int k = i; k <= matchEnd; k++) {
                    result[k] = REPLACE_CHAR;
                    replaced[k] = true;
                }
            }
        }

        return new String(result);
    }

    public boolean validate(String text) {
        return !containsSensitiveWord(text);
    }
}
