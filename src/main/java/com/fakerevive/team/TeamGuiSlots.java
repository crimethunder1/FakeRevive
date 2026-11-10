package com.fakerevive.team;

final class TeamGuiSlots {
    static final int INVENTORY_SIZE = 54;

    static final int MAIN_CREATE_TEAM = 49;

    static final int TEAM_BACK = 45;
    static final int TEAM_ADD_PLAYER = 46;
    static final int TEAM_CLEAR = 47;
    static final int TEAM_KIT_DISPLAY = 48;
    static final int TEAM_SET_KIT = 50;
    static final int TEAM_DELETE = 53;

    static final int KIT_BACK = 45;
    static final int KIT_REMOVE = 49;

    static final int MEMBER_FIRST = 0;
    static final int MEMBER_LAST = 27;
    static final int MEMBER_CAPACITY = MEMBER_LAST - MEMBER_FIRST + 1;

    private static final int INNER_FIRST_ROW = 1;
    private static final int INNER_LAST_ROW = 4;
    private static final int INNER_FIRST_COLUMN = 1;
    private static final int INNER_LAST_COLUMN = 7;
    private static final int COLUMNS_PER_ROW = 9;

    private TeamGuiSlots() {
    }

    static int[] innerSlots() {
        int rowCount = INNER_LAST_ROW - INNER_FIRST_ROW + 1;
        int columnCount = INNER_LAST_COLUMN - INNER_FIRST_COLUMN + 1;
        int[] slots = new int[rowCount * columnCount];
        int position = 0;
        for (int row = INNER_FIRST_ROW; row <= INNER_LAST_ROW; row++) {
            for (int column = INNER_FIRST_COLUMN; column <= INNER_LAST_COLUMN; column++) {
                slots[position++] = row * COLUMNS_PER_ROW + column;
            }
        }
        return slots;
    }
}
