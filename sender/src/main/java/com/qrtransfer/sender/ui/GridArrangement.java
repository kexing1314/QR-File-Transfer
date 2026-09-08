package com.qrtransfer.sender.ui;

public enum GridArrangement {
    HORIZONTAL, VERTICAL, SQUARE;

    /** 返回 {行数, 列数}；SQUARE 为近方阵、宽屏优先（列数 ≥ 行数）。 */
    public static int[] shape(int count, GridArrangement arrangement) {
        switch (arrangement) {
            case HORIZONTAL:
                return new int[]{1, count};
            case VERTICAL:
                return new int[]{count, 1};
            case SQUARE: {
                int cols = (int) Math.ceil(Math.sqrt(count));
                int rows = (int) Math.ceil((double) count / cols);
                return new int[]{rows, cols};
            }
            default:
                throw new IllegalStateException("unknown arrangement: " + arrangement);
        }
    }
}
