/*
 * Copyright 2003-2015 - Regents of the University of California, San
 * Francisco.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */
package isl.model;

import java.util.ArrayList;

public class InnerSpace extends SSGrid {
  private static final long serialVersionUID = 688049923018459132L;

  Tube core = null;
  public Tube getCore() { return core; }
  int flow = -Integer.MAX_VALUE;
  private static org.slf4j.Logger log = null;

  @Override
  public void setLogger(org.slf4j.Logger logger) {
    log = logger;
  }

  /**
   * Creates a new instance of InnerSpace
   */
  public InnerSpace(SS s, int w, int h, int fr) {
    super(s, w, h, null, null);
    // add coreOut() to the flowShuffler to give core solute a chance to jump to the rim
    flowShuffler.add((Runnable) () -> { coreOut(); });

    if (s != null) {
      ss = s;
    } else {
      throw new RuntimeException("InnerSpace: parent SS cannot be null.");
    }
    core = new Tube(w, h);
    if (fr > 0) {
      flow = fr;
    }
    /*
     * Add the core to the flow shuffler
     */
    flowShuffler.add((Runnable) () -> { advanceCore(flow); });
  }

  public int getRingCount() {
    int count = 0;
    for (int cNdx = 0; cNdx < width; cNdx++) {
      if (numObjectsAtLocation(cNdx, 0) >= 1) {
        count++;
      }
    }
    return count;
  }

  public int getInnerSpaceCount() {
    int count = 0;
    for (int cNdx = 0; cNdx < width; cNdx++) {
      for (int lNdx = 0; lNdx < height; lNdx++) {
        count += numObjectsAtLocation(cNdx, lNdx);
      }
    }
    for (int lNdx = 0; lNdx < height; lNdx++) {
      count += core.get(lNdx).size();
    }
    return count;
  }

  private void advanceCore(int flowRate) {
    // if core is supposed to be stagnant, bail out
    if (flow < 1) return;
    
    // solute in the last flowRate cells of the core gets distributed
    int start = height - flow;
    if (start < 0) start = 0;
    for (int yNdx = start; yNdx < height; yNdx++) {
      ArrayList<Solute> move = ss.distribute(core.get(yNdx), CompartmentType.GRID);
      if (move != null) for (Solute s : move) core.get(yNdx).remove(s);
    }

    // candidates that weren't distributed from the end of the core go up flowRate
    core.advance(flowRate);
  }

  public void coreOut() {
    /*
     * first give the chance for core solute to jump to the rim solutes list is
     * not changed
     * Dir = { E, W, Out, In, NE, N, NW, SW, S, SE }
     */
    ArrayList<Solute> jumped2Rim = new ArrayList<>();
    for (int coreNdx = 0; coreNdx < core.getLength(); coreNdx++) {
      ArrayList<Solute> corePt = core.get(coreNdx);
      for (Solute s : corePt) {
        double draw = ss.hepStruct.model.parent.bcRNG.nextDouble();
        if (probV.get(SSGrid.Dir.W) <= draw
                && draw < probV.get(SSGrid.Dir.Out)) {
          if (jump2Rim(s, coreNdx)) jumped2Rim.add(s);
        }
      }
      for (Solute s : jumped2Rim) {
        corePt.remove(s);
      }
      jumped2Rim.clear();
    }
  }

  private boolean jump2Rim(Solute s, int coreNdx) {
    boolean retVal = false;
    for (int xNdx = 0; xNdx < width; xNdx++) {
      if (numObjectsAtLocation(xNdx, coreNdx) <= 0) {
        setObjectLocation(s, xNdx, coreNdx);
        retVal = true;
        break;
      }
    }
    return retVal;
  }

  /**
   * Overrides SSGrid.jumpInward(s) to accommodate the core.
   * @param s
   * @return
   */
  @Override
  boolean flowInward(Solute s) {
    boolean retVal = false;
    sim.util.Int2D loc = getObjectLocation(s);
    if (ss.getInletCap() - core.get(loc.y).size() > 1.0) {
      core.get(loc.y).add(s);
      retVal = true;
    }
    return retVal;
  }

}
