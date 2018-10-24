/*
 * Copyright 2003-2018 - Regents of the University of California, San
 * Francisco.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * 
 */

package isl.model.cell;

import isl.model.Solute;

public class EC extends Cell {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger( EC.class );
  static sim.util.Double2D CROSS_PROB = new sim.util.Double2D(1.0, 1.0);
  public static void setMembraneCrossProb(sim.util.Double2D mcp) { log.debug("Setting mcp = "+mcp); CROSS_PROB = mcp; }
  @Override
  public boolean canCross(Solute s, Dir d) { return canCross(s,d,CROSS_PROB); }

  /**
   * @param p - parent is an SSGrid, usually ESpace
   * @param rng - locally held handle for pRNG
   */
  public EC(isl.model.SSGrid p, ec.util.MersenneTwisterFast rng) {
    super(p, rng);
    actionShuffler.add((Runnable) () -> { handleDegradation(myGrid); });
  }
    
}
