/*
 * Copyright 2003-2018 - Regents of the University of California, San
 * Francisco.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package isl.model;

import java.util.ArrayList;
import java.util.EnumMap;
import isl.model.cell.Cell;

public class SSGrid extends sim.field.grid.SparseGrid2D {
  private static final long serialVersionUID = -4798219758652047850L;

  private static org.slf4j.Logger log = null;
  public void setLogger(org.slf4j.Logger logger) {
     log = logger;
  }
  
  /**
   * rxnProdCount -- contains the sum of all reaction products we were told
   * to count.
   */
  public sim.field.grid.IntGrid2D rxnProdCount = null;

  /**
   * Increments the rxnProdCount value for this X,Y
   * @param st String name of the Solute type -- ignored for now because which
   * types are counted is handled in the caller (ReactionHandler)
   * @param x X Position of the Hepatocyte reporting the reaction
   * @param y Y Position of the Hepatocyte reporting the reaction
   */
  public void incRxnProd(String st, int x, int y) {
    rxnProdCount.set(x, y, rxnProdCount.get(x,y)+1);
  }

  // solute kept in main grid
  protected sim.field.grid.ObjectGrid2D celGrid = null;  // for cells
  public sim.field.grid.ObjectGrid2D getCellGrid() { return celGrid; }

  public SS ss = null;
  SSGrid inwardGrid = null;
  SSGrid outwardGrid = null;

  /**
   * allows us to p-randomly shuffle the order in which the inward, outward, and
   * moore movements execute
   */
  sim.util.Bag flowShuffler = null;

  public SSGrid(SS s, int w, int h, SSGrid in, SSGrid out) {
    super(w, h);
    ss = s;
    inwardGrid = in;
    outwardGrid = out;

    flowShuffler = new sim.util.Bag(3);
    if (out != null) setOutwardGrid(out);
    final SSGrid grid = this;
    flowShuffler.add(
            new Runnable() {
              // move around within the grid
              public void run() { moveMoore(grid,ss); }
            });
    if (in != null) setInwardGrid(in);
  }

  public void setInwardGrid(SSGrid in) {
    inwardGrid = in;
    flowShuffler.add(
            new Runnable() {
              public void run() {
                // if we're middle or outer, allow inward movement
                if (inwardGrid != null) flowInward();
              }
            });
  }
  public void setOutwardGrid(SSGrid out) {
    outwardGrid = out;
    flowShuffler.add(
            new Runnable() {
              public void run() {
                // if we're inner or middle, allow outward movement
                if (outwardGrid != null) flowOutward();
              }
            });
  }

  public void setProbV() {
    setProbV(SSParams.forward_bias, SSParams.lateral_bias);
  }
  public void setProbV(double forB, double latB) {
    probV = SSGrid.setProbV(inwardGrid, outwardGrid, SSParams.forward_bias, SSParams.lateral_bias);
  }
  public void flow () {
    flowShuffler.shuffle(ss.compRNG);
    for (Object o : flowShuffler) ((Runnable)o).run();
    /*
    // if we're inner or middle, allow outward movement
    if (outwardGrid != null) flowOutward();
    moveMoore(this, ss);
    // if we're middle or outer, allow inward movement
    if (inwardGrid != null) flowInward();
    */
  }

  void flowInward() {
    ArrayList<Solute> jumpedInward = new ArrayList<>();
    for (int wNdx = 0; wNdx < width; wNdx++) {
      for (int hNdx = 0; hNdx < height; hNdx++) {
        sim.util.Bag objects = getObjectsAtLocation(wNdx, hNdx);
        if (objects != null) {
          // if celGrid == null, we're an InnerSpace
          Cell c = (celGrid == null ? null : (Cell)celGrid.get(wNdx,hNdx));
          for (Object o : objects) {
            Solute s = (Solute) o;
            boolean isInOldCell = false;
            if (c == null || !(isInOldCell = c.listSolute().contains(s)) || c.canCross(s,Cell.Dir.OUT)) {
              double draw = ss.compRNG.nextDouble();
              if (probV.get(SSGrid.Dir.Out) <= draw
                      && draw < probV.get(SSGrid.Dir.In)) {
                if (flowInward(s)){
                  jumpedInward.add(s);
                  if (isInOldCell) c.remove(s);
                }
              }
            } // end if (c == null || (!c.bound.containsValue(s) && membraneCrossing)) {
          }
        }
      } // end - for (int hNdx = 0; hNdx < height; hNdx++) {
    } // end - for (int wNdx = 0; wNdx < width; wNdx++) {
    for (Solute s : jumpedInward) {

      //if (ss.id == 39) {
      //  sim.util.Int2D l = getObjectLocation(s);
      //  Cell c = (celGrid != null ? (Cell)celGrid.get(l.x,l.y)  :  null);
      //  Cell tgtCell = (inwardGrid.celGrid != null ? (Cell)inwardGrid.celGrid.get(l.x,l.y) : null);
      //  log.debug(this+" "+s+" jumpedInward from cell "+c+" at "+l+" into "+tgtCell );
      //}

      remove(s);
    }
  }

  boolean flowInward(Solute s) {
    return jump2Space(s, inwardGrid);
  }

  void flowOutward() {
    ArrayList<Solute> jumpedOutward = new ArrayList<>();
    for (int wNdx = 0; wNdx < width; wNdx++) {
      for (int hNdx = 0; hNdx < height; hNdx++) {
        sim.util.Bag objects = getObjectsAtLocation(wNdx, hNdx);
        if (objects != null) {  // if there are objects to move
          // if celGrid == null, we're an innerspace
          Cell c = (celGrid == null ? null : (Cell)celGrid.get(wNdx,hNdx));
          for (Object o : objects) {
            Solute s = (Solute) o;
            boolean isInOldCell = false;
            if (c == null || !(isInOldCell = c.listSolute().contains(s)) || c.canCross(s,Cell.Dir.OUT)) {
              double draw = ss.compRNG.nextDouble();
              if (probV.get(SSGrid.Dir.W) <= draw
                      && draw < probV.get(SSGrid.Dir.Out)) {
                if (flowOutward(s)) {
                  jumpedOutward.add(s);
                  // remove the moving solute from the cell's list
                  if (isInOldCell) c.remove(s);
                }
              }
            } // end if (c == null || !c.bound.containsValue(s)) {
          }  // end for (Object o : objects) {
        } // no objects to move
      } // end - for (int hNdx = 0; hNdx < height; hNdx++) {
    } // end - for (int wNdx = 0; wNdx < width; wNdx++) {
    for (Solute s : jumpedOutward) {

      //if (ss.id == 39) {
      //  sim.util.Int2D l = getObjectLocation(s);
      //  Cell c = (celGrid != null ? (Cell)celGrid.get(l.x,l.y)  :  null);
      //  Cell tgtCell = (outwardGrid.celGrid != null ? (Cell)outwardGrid.celGrid.get(l.x,l.y) : null);
      //  log.debug(this+" "+s+" jumpedOutward from cell "+c+" at "+l+" into "+tgtCell);
      //}

      remove(s);

    }
  }
  
  boolean flowOutward(Solute s) {
    return jump2Space(s, outwardGrid);
  }

 /**
  * Jump2Space checks properties of destination.  Flow[Inward|Outward] check source properties.
  * @param s
  * @param g
  * @return 
  */
  boolean jump2Space(Solute s, SSGrid g) {
    boolean retVal = false;
    sim.util.Int2D loc = getObjectLocation(s);
    Cell cell = (g.celGrid == null ? null : (Cell) g.celGrid.get(loc.x, loc.y));
    boolean isRoom = (g.numObjectsAtLocation(loc.x, loc.y) < StrictMath.floor(ss.hepStruct.model.scale));
    boolean cellOK = true;
    if (isRoom) {
      if (cell != null) { // cell exists, query and add
        cellOK = cell.canCross(s, Cell.Dir.IN);
        if (cellOK) cell.add(s);
      }
      if (cellOK) { // cell null or not, add to grid
        g.setObjectLocation(s, loc);
        retVal = true;
      }
    }
    return retVal;
  }

  // TRY_LIMIT stopping criterion for infinite loops
  public static final int TRY_LIMIT = 10;

  public enum Dir { E, W, Out, In, NE, N, NW, SW, S, SE };

  public java.util.EnumMap<Dir,Double> probV = null;
  /**
   *
   * @param for_bias [0,1] 0 => N/S directions equal, 1 => S biased
   * @param lateral_bias [0,1] 0 => full in bias, 1 => full out bias
   */
  public static EnumMap<Dir,Double> setProbV(SSGrid inGrid, SSGrid outGrid, double for_bias, double lateral_bias) {
    EnumMap<Dir, Double> retVal = new EnumMap<>(Dir.class);
    Dir[] d = Dir.values();
    int total_directions = d.length+1; // to include staying put
    if (inGrid == null) total_directions--;
    if (outGrid == null) total_directions--;

    double base = 1.0/total_directions;
    double lateral = base;
    retVal.put(Dir.E, lateral);
    retVal.put(Dir.W, 2*lateral);

    /**
     * modifier for out vs. in bias
     * bottom and top stay the same for combined out+in
     * top of out changes depending on the value of lateral_bias
     */
    if (outGrid != null && inGrid != null) {
      double unmodified_in_top = 4*lateral; // set 4 lateral increments up
      double out_and_in_bins_size = 2*lateral;  // 2 laterals in size
      double modified_out_top = lateral_bias * out_and_in_bins_size
              + retVal.get(Dir.W);
      retVal.put(Dir.Out, modified_out_top);
      retVal.put(Dir.In, unmodified_in_top);
    } else if (outGrid == null && inGrid == null) {
      // set both bins to zero size
      retVal.put(Dir.Out, retVal.get(Dir.W));
      retVal.put(Dir.In, retVal.get(Dir.Out));
    } else if (outGrid != null) {
      retVal.put(Dir.Out, retVal.get(Dir.W)+lateral);
      retVal.put(Dir.In, retVal.get(Dir.Out)); // zero In bin size
    } else { // inGrid != null
      retVal.put(Dir.Out, retVal.get(Dir.W)); // zero Out bin size
      retVal.put(Dir.In, retVal.get(Dir.Out)+lateral);
    }


    double northward = base*(1-for_bias);
    double northbottom = retVal.get(Dir.In);
    retVal.put(Dir.NE, northbottom+northward);
    retVal.put(Dir.N, northbottom+2*northward);
    retVal.put(Dir.NW, northbottom+3*northward);
    double southward = base*(1+for_bias);
    double southbottom = northbottom+3*northward;
    retVal.put(Dir.SW, southbottom+southward);
    retVal.put(Dir.S, southbottom+2*southward);
    retVal.put(Dir.SE, southbottom+3*southward);
    // no entry for stay hwere we are

    /*{ // debug clause
      StringBuilder debugString = new StringBuilder(SSGrid.class + ".setProbV(" + for_bias
              + ", " + lateral_bias + ") ");
      debugString.append("probV [E, W, Out, In, NE, N, NW, SW, S, SE] = \n");
      for (int dNdx = 0; dNdx < d.length; dNdx++) {
        debugString.append("     " + retVal.get(d[dNdx]) + "\n");
      }
      log.debug(debugString.toString());
    }*/


    if (retVal.get(Dir.E) < 0.0 || retVal.get(Dir.SE) > 1.0)
      throw new RuntimeException("probV calculation wrong! min = "+retVal.get(Dir.E)+", max = "+retVal.get(Dir.SE));

    return retVal;
  }

  public void moveMoore(SSGrid grid, SS ss) {
    //if (ss.id == 43)
    //   log.debug(this+".moveMoore("+grid+", "+ss+":"+ss.id+")");
    
    /*
     * move solute within the rim solutes unchanged
     */
    ArrayList<Solute> exitingRim = new ArrayList<>();
    for (int wNdx=0 ; wNdx<grid.getWidth() ; wNdx++) {
      for (int hNdx=0 ; hNdx<grid.getHeight() ; hNdx++) {
        sim.util.Bag objects = grid.getObjectsAtLocation(wNdx,hNdx);
        // continue to next point if nothing's here
        if (objects != null) {
          // if celGrid == null, we're an innerspace
          Cell oldCell = (grid.celGrid == null ? null : (Cell)grid.celGrid.get(wNdx,hNdx));
          boolean isInOldCell = false;
          // try to move the solute(s)
          for (Object o : objects) {
            // solute bound inside cells or unable to partition out do not move
            Solute s = (Solute) o;
            if (oldCell == null || !(isInOldCell = oldCell.listSolute().contains(s)) || oldCell.canCross(s,Cell.Dir.OUT)) {

              sim.util.Int2D newLoc = null;
              sim.util.Int2D oldLoc = new sim.util.Int2D(wNdx,hNdx);
              double draw = ss.compRNG.nextDouble();

              //if (ss.id == 43)
              //  log.debug(this+" "+s+" draw = "+draw);

              if (0.0 <= draw
                      && draw < probV.get(SSGrid.Dir.E))
                newLoc = new sim.util.Int2D(grid.tx(wNdx+1), hNdx);
              else if (probV.get(SSGrid.Dir.E) <= draw
                      && draw < probV.get(SSGrid.Dir.W))
                newLoc = new sim.util.Int2D(grid.tx(wNdx-1), hNdx);
              else if (probV.get(SSGrid.Dir.In) <= draw
                      && draw < probV.get(SSGrid.Dir.NE))
                newLoc = new sim.util.Int2D(grid.tx(wNdx+1), grid.ty(hNdx-1));
              else if (probV.get(SSGrid.Dir.NE) <= draw
                      && draw < probV.get(SSGrid.Dir.N))
                newLoc = new sim.util.Int2D(wNdx, grid.ty(hNdx-1));
              else if (probV.get(SSGrid.Dir.N) <= draw
                      && draw < probV.get(SSGrid.Dir.NW))
                newLoc = new sim.util.Int2D(grid.tx(wNdx-1), grid.ty(hNdx-1));
              else if (probV.get(SSGrid.Dir.NW) <= draw
                      && draw < probV.get(SSGrid.Dir.SW))
                newLoc = new sim.util.Int2D(grid.tx(wNdx-1), hNdx+1);
              else if (probV.get(SSGrid.Dir.SW) <= draw
                      && draw < probV.get(SSGrid.Dir.S))
                newLoc = new sim.util.Int2D(wNdx, hNdx+1);
              else if (probV.get(SSGrid.Dir.S) <= draw
                      && draw < probV.get(SSGrid.Dir.SE))
                newLoc = new sim.util.Int2D(grid.tx(wNdx+1), hNdx+1);

              //else if (ss.id == 43) log.debug(this+" "+s+" staying put");
              
              // else stay put

              // actually move the solute(s)
              /** restrict movement to grid points with rejecting Cells */
              if (newLoc != null) {
                if (newLoc.y < height) {
                  Cell newCell = (grid.celGrid == null ? null : (Cell) grid.celGrid.get(newLoc.x, newLoc.y));
                  boolean isRoom = (grid.numObjectsAtLocation(newLoc.x, newLoc.y) < StrictMath.floor(ss.hepStruct.model.scale));
                  boolean cellOK = true;
                  //if (isRoom) cellOK = (newCell == null ? true : newCell.accept(s));
                  if (isRoom) {
                    if (newCell != null) { // newCell exists, query and add
                      cellOK = newCell.canCross(s, Cell.Dir.IN);
                      if (cellOK) newCell.add(s);
                    }
                    if (cellOK) { // newCell null or not, add to the grid
                      grid.setObjectLocation(s, newLoc);
                      if (isInOldCell) oldCell.remove(s);
                    }
                  }
                } else {
                  // only allow exiting from the InnerSpace
                  if (this instanceof isl.model.InnerSpace) exitingRim.add(s);
                }
              } // end if (newLoc != null) {

            } // end  if (cell == null || (!cell.bound.containsValue(s) && membraneCrossing)) {
          } // end for (Object s : objects) {

          // before changing x,y handle those that exit
          ArrayList<Solute> exitedRim = ss.distribute(exitingRim, CompartmentType.GRID);
          if (exitedRim != null) {
              for (Solute s : exitedRim) {
              //log.debug(this+" "+s+" at "+grid.getObjectLocation(s)+" exitedRim from cell "+oldCell+" at <"+(oldCell != null ? oldCell.myX+", "+oldCell.myY : "null, null")+">");
              grid.remove(s);
              // remove from its old cell if it was in one
              if (isInOldCell) {
                oldCell.remove(s);
              }
            }
            exitingRim.clear();
          }


        } // end if (objects != null) {
      } // end - for (int hNdx=0 ; hNdx<height ; hNdx++)
    } // end - for (int wNdx=0 ; wNdx<width ; wNdx++)


  }

  public ArrayList<Solute> getSoluteAtY(int y) {
    ArrayList<Solute> retVal = new ArrayList<>();
    for (int x=0 ; x<width ; x++) {
      sim.util.Bag b = getObjectsAtLocation(x,y);
      if (b != null) retVal.addAll(b);
    }
    return retVal;
  }
  
  public ArrayList<Cell> getCellsAtY(int y) {
    ArrayList<Cell> retVal = null;
    if (celGrid != null) {
      retVal = new ArrayList<>();
      for (int x=0 ; x<width ; x++) {
        Cell c = (Cell)celGrid.get(x,y);
        if ( c != null ) retVal.add(c);
      }
    }
    return retVal;
  }
}
