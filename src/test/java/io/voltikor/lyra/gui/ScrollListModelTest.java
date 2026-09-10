package io.voltikor.lyra.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScrollListModelTest {
   @Test
   void largeLibraryOnlyVisitsVisibleRowsIncludingPartialOnes() {
      var model = new ScrollListModel();
      model.resize(100_000, 100);
      model.scrollTo(22_011);
      assertEquals(1000, model.first());
      assertEquals(1006, model.end());
      assertEquals(1000, model.rowAt(0));
      assertEquals(1001, model.rowAt(11));
      assertEquals(-1, model.rowAt(-1));
      assertEquals(-1, model.rowAt(100));
   }

   @Test
   void scrollingClampsAtBothEndsAndAfterFiltering() {
      var model = new ScrollListModel();
      model.resize(100, 44);
      model.scrollTo(Double.MAX_VALUE);
      assertEquals(98, model.first());
      assertEquals(100, model.end());
      model.resize(1, 44);
      assertEquals(0, model.offset());
      assertEquals(1, model.end());
      assertEquals(-1, model.rowAt(30));
      model.scrollBy(-100);
      assertEquals(0, model.offset());
      model.resize(0, 44);
      assertEquals(0, model.end());
      assertEquals(-1, model.rowAt(0));
   }

   @Test
   void keyboardNavigationRevealsFirstAndLastItem() {
      var model = new ScrollListModel();
      model.resize(20, 66);
      model.reveal(19);
      assertEquals(17, model.first());
      assertEquals(20, model.end());
      model.reveal(0);
      assertEquals(0, model.offset());
   }
}
