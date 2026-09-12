package io.voltikor.lyra.gui.screen;

final class ScrollListModel {
   static final int ROW_HEIGHT = 22;
   private int count;
   private int viewport;
   private double offset;

   void resize(int count, int viewport) {
      this.count = Math.max(0, count);
      this.viewport = Math.max(1, viewport);
      scrollTo(offset);
   }

   void scrollTo(double value) { offset = Math.clamp(value, 0, maximum()); }
   void scrollBy(double value) { scrollTo(offset + value); }
   double offset() { return offset; }
   double maximum() { return Math.max(0, (double) count * ROW_HEIGHT - viewport); }
   int first() { return Math.min(count, (int) (offset / ROW_HEIGHT)); }
   int end() { return Math.min(count, (int) Math.ceil((offset + viewport) / ROW_HEIGHT)); }
   int rowAt(double y) {
      if (y < 0 || y >= viewport) return -1;
      int row = (int) ((y + offset) / ROW_HEIGHT);
      return row < count ? row : -1;
   }
   void reveal(int row) {
      if (row < 0 || row >= count) return;
      double top = (double) row * ROW_HEIGHT;
      if (top < offset) scrollTo(top);
      else if (top + ROW_HEIGHT > offset + viewport) scrollTo(top + ROW_HEIGHT - viewport);
   }
}
