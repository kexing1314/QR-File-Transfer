package com.qrtransfer.sender.ui;

public enum GridArrangement {
    HORIZONTAL, VERTICAL, SQUARE;

    /** 返回 {行数, 列数}；SQUARE 对 2 张退化为 1×2。 */
    public static int[] shape(int count, GridArrangement arrangement) {
        switch (arrangement) {
            case HORIZONTAL:
                return new int[]{1, count};
            case VERTICAL:
                return new int[]{count, 1};
            case SQUARE:
                return count == 4 ? new int[]{2, 2} : new int[]{1, count};
            default:
                throw new IllegalStateException("unknown arrangement: " + arrangement);
        }
    }
}
