package com.depchain.consensus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

public class ViewManagerTest {
    
    private ViewManager viewManager;
    
    @BeforeEach
    void setUp() {
        viewManager = new ViewManager();
    }
    
    @Test
    void testGetLeaderForView() {
        assertEquals(1, viewManager.getLeaderForView(1, 4));
        assertEquals(0, viewManager.getLeaderForView(100, 4));

        assertEquals(0, viewManager.getLeaderForView(6, 3));
        assertEquals(1, viewManager.getLeaderForView(7, 3));
    }
    
    @Test
    void testIsLeader() {
        assertTrue(viewManager.isLeader(0, 0));
        assertTrue(viewManager.isLeader(3, 3));
    }
    
    @Test
    void testGetLeaderIdforView() {
        assertEquals(0, viewManager.getLeaderIdforView(0, 4));
        assertEquals(1, viewManager.getLeaderIdforView(1, 4));
        assertEquals(0, viewManager.getLeaderIdforView(4, 4)); 
        assertEquals(1, viewManager.getLeaderIdforView(13, 4));
        assertEquals(2, viewManager.getLeaderIdforView(14, 4));
    }
}
