/*
 * Copyright 2003-2018 - Regents of the University of California, San
 * Francisco.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package isl.model.cell;

import isl.model.EnzymeGroup;
import isl.model.MetabolicEnvironment;
import isl.model.SSGrid;
import isl.model.Solute;
import sim.util.Double2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class Cell implements BindingInfo {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger( Cell.class );
  private static int instanceCount = 0;
  public int id = -1;

  public static MetabolicEnvironment MET_ENV = null;
  
  /**
   * ∃ static CROSS_PROB and canCross() in each subclass to avoid using an IVar
   * and still allow each subclass to have its own CROSS_PROB. There must be
   * a better way to implement this pattern.
   */
  static sim.util.Double2D CROSS_PROB = new sim.util.Double2D(1.0, 1.0);
  public static void setMembraneCrossProb(sim.util.Double2D mcp) { log.debug("Setting mcp = "+mcp); CROSS_PROB = mcp; }

  isl.model.SSGrid myGrid = null;
  ec.util.MersenneTwisterFast cellRNG = null;
  protected ArrayList<Solute> solutes = new ArrayList<>();
  
  public Map<String,Number> entries = new HashMap<>();
  public Map<String,Number> exits = new HashMap<>();
  public Map<String,Number> rejects = new HashMap<>();
  public Map<String,Number> traps = new HashMap<>();
  
  protected int repairCount = 0; // for counting Repair events
  public int getRepairCount() {return repairCount; }
  public void incRepairCount() {repairCount++;}
  
  private ArrayList<EnzymeGroup> groups = new ArrayList<>();

  ArrayList<Runnable> actionShuffler = new ArrayList<>();

  public int myX = -Integer.MAX_VALUE;
  public int myY = -Integer.MAX_VALUE;
  public void setLoc(int x, int y) {
    if (y < 0 || x < 0) throw new RuntimeException(this+" invalid position ("+x+", "+y+")");
    myX = x;
    myY = y;
    id = instanceCount++;
  }

  public Cell(isl.model.SSGrid p, ec.util.MersenneTwisterFast rng) {
    if (p != null) myGrid = p;
      cellRNG = rng;
      actionShuffler.add(new BindingHandler((BindingInfo) this, cellRNG, log));
  }
 
  public void init() { createEnzymeGroups(); }
  
  public void createEnzymeGroups() {
    //Get list of EnzymeGroup names for this Cell type
    ArrayList<String> groupNames = MET_ENV.getCellTypeToEnzymeGroupNames().get(this.getClass().getSimpleName());
    if (groupNames == null) return;
    for(String name : groupNames) {
      EnzymeGroup eg = MET_ENV.enzymeGroups.get(name);
      if (eg == null) throw new RuntimeException("∄ group named "+name);
      int ic = cellRNG.nextInt(getBindmax()-getBindmin()) + getBindmin();
      if (eg.hasProperty("graded") && ((boolean)eg.getProperty("graded"))) {
        throw new RuntimeException(this.getClass().getSimpleName()+" has no basis for an "+eg.type+" gradient.");
      }
      EnzymeGroup egcopy = new EnzymeGroup(eg.type,
              ic,
              eg.getBindProb(),
              eg.getBindCycles(),
              eg.getAcceptedSolutes(),
              eg.getProperties());
      egcopy.downRegulatedBy = eg.downRegulatedBy;
      addEnzymeGroup(egcopy);
    }
  }
  public static enum Dir {IN, OUT};
  public boolean canCross(Solute s, Dir d) { return canCross(s,d,CROSS_PROB); }
  public boolean canCross(Solute s, Dir d, Double2D mcp) {
    boolean retVal = (s.hasProperty("membraneCrossing") && (boolean)s.getProperty("membraneCrossing"));
    
    // if not membraneCrossing, but can be transported out
    if (!retVal && d == Dir.OUT)
      retVal = s.hasProperty("transportOut") && ((boolean) s.getProperty("transportOut"));

    // if outward and bound, can't cross
    if (retVal && d == Dir.OUT && isBound(s)) retVal = false;
    
    // see if the Solute overrides the model-wide cellEnterExitProb
    if (retVal && s.hasProperty("cellEnterExitProb")) {
      Map<String, Double2D> ceepm = (Map<String,Double2D>)s.getProperty("cellEnterExitProb");
      String thisCellType = this.getClass().getSimpleName();
      if (ceepm.keySet().contains(thisCellType)) {
        Double2D newMCP = ceepm.get(thisCellType);
        //log.debug("Overriding cellEnterExitProb = "+mcp+" with "+newMCP);
        mcp = newMCP;
      }
      // else if this cell type isn't found, go ahead with model-wide mcp
    }
    // if pRNG draw fails, can't move
    if (retVal) {
      double draw = cellRNG.nextDouble();
      if (draw >= (d == Dir.IN ? mcp.x : mcp.y)) {  // can cross, but FAILED the draw
        retVal = false;
      }
    }
    // only count failures, success are counted in add() and remove()
    if (d == Dir.IN && !retVal) countMove(s,rejects);
    if (d == Dir.OUT && !retVal) countMove(s, traps);

    return retVal;
  }
  
  public void add(Solute s) {
    solutes.add(s);
    countMove(s, entries);
  }
  
  public boolean remove(Solute s) {
    return remove(s,false);
  }
  public boolean remove(Solute s, boolean leak) {
    if (!solutes.contains(s)) {
      throw new RuntimeException("Don't remove "+s.type+"'s that aren't there!");
    }
    if (!leak) countMove(s, exits);
    return solutes.remove(s);
  }

  /**
   * For Solute the Cell has created (by whatever means) and needs to present
   * in its data structures and the grid, etc.
   * @param s 
   */
  public void present(Solute s) {
    myGrid.ss.solutes.add(s);
    myGrid.setObjectLocation(s, myX, myY);
    solutes.add(s);
  }
  
  /**
   * Remove the Solute from data structures and allow the system to reclaim the
   * memory.  In the analogy, this/these molecules no longer exist. This method
   * does NOT handle bound Solute.
   * @param s 
   */
  public void forget(Solute s) {
    solutes.remove(s); //remove it from my list
    myGrid.ss.solutes.remove(s); //remove it from parent's list
    // -- this will be a problem when/if hepatocytes can live in another grid
    myGrid.remove(s); //remove it from the hSpace grid
  }
  
  void countMove(Solute s, Map<String,Number> counter) {
    if (counter.containsKey(s.type)) counter.replace(s.type,counter.get(s.type).intValue() + 1);
    else counter.put(s.type,1);
  }
  
  /**
   * Periodic "step" actions.  (Not named "step()" to avoid confusion with Steppables.
   */
  public void iterate() {
    bsg.util.CollectionUtils.shuffle(actionShuffler, cellRNG).stream().forEach((o) -> { ((Runnable)o).run(); });
  }

  protected void scheduleRelease(Solute s, long cycle) {
    isl.model.ISL model = myGrid.ss.hepStruct.model;
    //log.debug(tis+" scheduling "+b+" to release "+bound.get(b)+".type = "+bound.get(b).type+" at "+ cycle
    //        + " cycles = "+cycle);
    model.parent.schedule.scheduleOnce(cycle, 1, (sim.engine.SimState state) -> {
      //log.debug(this+" "+fb+" releases "+ bound.get(fb)+".type = "+bound.get(fb).type);
      for(EnzymeGroup eg : getEnzymeGroups()) { eg.getBoundSolutes().remove(s); }
    });
  }
      
  //Returns all bound Solutes in this Cell.
  public ArrayList<Solute> getAllBoundSolutes() {
    ArrayList<Solute> retVal = new ArrayList<>();
    for(EnzymeGroup eg : getEnzymeGroups()) {
      for(Solute s : eg.getBoundSolutes()) retVal.add(s);
    }
    return retVal;
  }
    
  //Returns whether Solute s is bound somewhere in this Cell.
  @Override
  public boolean isBound(Solute s) {
    //return getAllBoundSolutes().contains(s);
    return getEnzymeGroups().stream().anyMatch((eg) -> (eg.getBoundSolutes().contains(s)));
  }

  @Override
  public int getBindmin() { return MET_ENV.bindmin; }
  @Override
  public int getBindmax() { return MET_ENV.bindmax; }
  
  //Returns sum of capacity for all EnzymeGroups in this Cell.
  public int getTotalCapacity() {
    int retVal = 0;
    for(EnzymeGroup eg : getEnzymeGroups()) retVal += eg.getCapacity();
    return retVal;
  }
    
  //Implementations for BindingInfo
  
  /** 
   * Returns a flattened copy of List<ArrayList<EnzymeGroup>> groups
   * @return ArrayList<EnzymeGroup>
   */
  @Override
  public ArrayList<EnzymeGroup> getEnzymeGroups() {return groups;}
  
  @Override
  public List<Solute> listSolute() { return java.util.Collections.unmodifiableList(solutes); }
  
  Object[] bsp_ivals = new Object[3];
  @Override
  public double getBindingProbability(EnzymeGroup eg) {
    double fraction = Double.NaN;
    int bound = eg.getBoundSolutes().size();
    if(MET_ENV.getBindingMode().equals("stepwise")) {
      fraction = (eg.capacity > 0 ? 1.0 : 0.0);
    } else if(MET_ENV.getBindingMode().equals("linear")) {
      fraction = (bound >= eg.capacity
              ? 0.0
              : (eg.getCapacity() - bound) / eg.getInitialCapacity());
    } else {
	    fraction = 0.0;
    }
    
    double exp_factor = 1.0;
    if (eg.hasProperty("bindExpFactor") && eg.getProperty("bindExpFactor") != null) {
      exp_factor = ((Double) eg.getProperty("bindExpFactor"));
    }

    return java.lang.Math.pow(fraction, exp_factor) * eg.getBindProb();
  }
    
  @Override
  public void scheduleRelease(Solute s, EnzymeGroup eg) {
    scheduleRelease(s, myGrid.ss.hepStruct.model.getCycle()+eg.getBindCycles());
  }
    
  public void handleDegradation(SSGrid space) {
    ArrayList<Solute> toRemove = new ArrayList<>();
    //For each Solute in this Cell
    for (Solute s : solutes) {
      //If degradable and unbound
      if (s.hasProperty("pDegrade")
              && ((Double) s.getProperty("pDegrade")) > 0.0
              && !isBound(s)) {
        double draw = cellRNG.nextDouble();
        if (draw <= ((Double) s.getProperty("pDegrade"))) {
          toRemove.add(s);
        }
      }
    }

    //Actually remove them
    for (Solute s : toRemove) {
      forget(s);
    }
  }

  public void addEnzymeGroup(EnzymeGroup ng) {
    if (groups.size() <= 0) groups.add(ng);
    else {
      int ng_order = 0;
      if (ng.hasProperty("ordering")) ng_order = (int)ng.getProperty("ordering");
      for (int gNdx=groups.size()-1 ; gNdx>-1 ; gNdx--) {
        EnzymeGroup eg = groups.get(gNdx);
        int eg_order = 0;
        if (eg.hasProperty("ordering")) eg_order = (int)eg.getProperty("ordering");
        if (ng_order < eg_order)  groups.add(gNdx, ng);
        else groups.add(gNdx+1, ng);
        break;
      }
    }
  }
}
