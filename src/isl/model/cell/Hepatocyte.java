/*
 * Copyright 2003-2018 - Regents of the University of California, San
 * Francisco.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package isl.model.cell;

import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import sim.util.Double2D;
import sim.util.distribution.Binomial;
import bsg.util.LinearGradient;
import bsg.util.SigmoidGradient;
import isl.model.EnzymeGroup;
import isl.model.MetabolicParams;
import isl.model.SSGrid;
import isl.model.Solute;
import isl.model.SoluteType;

public class Hepatocyte extends Cell implements CellInfo, EIInfo, ELInfo, ReactionInfo {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger( Hepatocyte.class );
  static Double2D CROSS_PROB = new Double2D(1.0,1.0);
  public static void setMembraneCrossProb(Double2D mcp) { log.debug("Setting mcp = "+mcp); CROSS_PROB = mcp; }
  @Override
  public boolean canCross(Solute s, Dir d) { return canCross(s,d,CROSS_PROB); }


  /**
   * rxnProbMap - map of metabolism probabilities for each Enzyme Group
   */
  Map<EnzymeGroup, Double> rxnProbMap = new LinkedHashMap<>();
  Map<EnzymeGroup, Double> rxnProbMapOrig = new LinkedHashMap<>();
  /**
   * productionMap - map from reaction product to it's ration of 1.0
   */
  Map<EnzymeGroup, Map<String,Double>> productionMap = new LinkedHashMap<>();
  public ArrayList< ArrayList<Integer> > elimQueues = null;    
  Binomial myBinomial = null;  
  
  public Hepatocyte(SSGrid p, ec.util.MersenneTwisterFast rng, int x, int y) {
    super(p, rng);
    setLoc(x,y);
    myBinomial = new Binomial(5,0.5,rng);
    actionShuffler.add((Runnable) () -> { handleDegradation(myGrid); });
  }
  
  Map<String,Double> elInhibTypes = new HashMap<>();

  public void present(Solute s, boolean bileAndAmp) {
    super.present(s);
    if (bileAndAmp) bileAndAmpHandling(s);
  }
  
  public void init(isl.io.HepInit hinit) {
    Map<String,Integer> hr = hinit.popHepatocyteRecord(id, cellRNG);
    dist_pv = hr.get("dPV");
    dist_cv = hr.get("dCV");
    super.init();
    finishInit();
  }
  @Override
  public void init() {
    dist_pv = myGrid.ss.priorPathLength + myY;
    dist_cv = ((myGrid.ss.length-1) - myY) + myGrid.ss.postPathLength;
    super.init();
    finishInit();
  }
  
  float ENZYME_INIT_FACTOR = 3.0f;
  @Override
  public void createEnzymeGroups() {
    //Get list of EnzymeGroup names for this Cell type
    java.util.ArrayList<String> groupNames = MET_ENV.getCellTypeToEnzymeGroupNames().get(this.getClass().getSimpleName());
    for(String name : groupNames) {
      EnzymeGroup eg = MET_ENV.enzymeGroups.get(name);
      if (eg == null) throw new RuntimeException("Can't find "+name+".");
      int ic = -Integer.MAX_VALUE;
      if (eg.hasProperty("graded") && ((boolean)eg.getProperty("graded"))) {
        float ratio = 1.0f + ENZYME_INIT_FACTOR * dist_pv/(dist_pv + dist_cv);
        int min = StrictMath.round(ratio * getBindmin());
        int max = StrictMath.round(ratio * getBindmax());
        ic = cellRNG.nextInt(max-min) + min;
        //log.debug("H:"+id+":EG."+eg.type+" -- dist_pv = "+dist_pv+", dist_cv = "+dist_cv+", ratio = "+ratio+", init capacity = "+ic);
      } else {
        ic = cellRNG.nextInt(getBindmax()-getBindmin()) + getBindmin();
        //log.debug("H:"+id+":EG."+eg.type+" -- init capacity = "+ic);
      }
      // deep copy of eg
      EnzymeGroup egd = new EnzymeGroup(eg.type,
              ic,
              eg.getBindProb(),
              eg.getBindCycles(),
              eg.getAcceptedSolutes(),
              eg.getProperties());
      // calculate gradients
      setGradients(egd, dist_pv, dist_cv);
      egd.downRegulatedBy = eg.downRegulatedBy;
      addEnzymeGroup(egd);
    }
  }
  
  // debug gradients
  static int gradDebugHs = 0;
  
  private void setGradients(EnzymeGroup eg, int dPV, int dCV) {
    // bail if this EG does not catalyze
    if (!eg.hasProperty("rxnProbStart")) return;
    // setup rxnProbMap
    double rps = (Double)eg.getProperty("rxnProbStart");
    double rpf = (Double)eg.getProperty("rxnProbFinish");
    double rp = Double.NaN;
    String rpg = (String)eg.getProperty("rxnProbGradient");
    if (rpg == null || rpg.contains("linear")) {
      rp = LinearGradient.eval(rps,rpf,0.0,(double)(dPV+dCV),(double)dPV);
    } else if (rpg.contains("sigmoid")) {
      rp = SigmoidGradient.eval(rps,rpf,0.0,(double)(dPV+dCV),(double)dPV);
    } else 
      throw new RuntimeException("Hepatocyte("+id+") - Unrecognized rxnProbGradient value: "+rpg);
    rxnProbMap.put(eg, rp);

    // log rxnPro[bd] gradients for 5 Hs
    boolean debugGrad = (gradDebugHs < 5*MET_ENV.enzymeGroups.size()); gradDebugHs++;
    StringBuilder gradDebug = new StringBuilder();
    if (debugGrad) gradDebug.append("H:"+id+".rxnProb(eg="+eg.type+",start="+rps+",finish="+rpf+",dPV="+dPV+",dCV="+dCV+") ⇒ rp="+rp+"\n");
      
    // setup productionMap
    if (rp > 0.0) {
      Map<String, Double2D> rxnprodmap = (Map<String,Double2D>)eg.getProperty("rxnProducts");
      Map<String, Double> rxnProdMap = new LinkedHashMap<>();
      for (Map.Entry<String, Double2D> me : rxnprodmap.entrySet()) {
        Double2D d2d = me.getValue();
        double prmin = d2d.x, prmax = d2d.y;
        double prodRate = LinearGradient.eval(prmin, prmax, 0.0, (double) dPV+dCV, (double)dPV);
        rxnProdMap.put(me.getKey(), prodRate);
        
        // gradient debug
        if (debugGrad) gradDebug.append(me.getKey()+":<"+prmin+","+prmax+"> ⇒ "+prodRate+"\n");
        
      }

      productionMap.put(eg, rxnProdMap);

      // gradient debug
      if (debugGrad) log.debug(gradDebug.toString());
      
    }
  }
  
  public void finishInit() {
    // list of EnzymeGroups is needed for E[IL]Handler constructors and downRegulation runnable
    if (myGrid.ss.ei_rate > 0.0) {
      actionShuffler.add(new EIHandler((CellInfo) this, (BindingInfo) this, (EIInfo) this, log));
    }
    if (myGrid.ss.el_rate > 0.0) {
      actionShuffler.add(new ELHandler((CellInfo) this, (BindingInfo) this, (ELInfo) this, log));
    }
    if (MET_ENV.drInterval > 0) {
      //The entire mechanism is repeated for each EnzymeGroup for which downRegulated = true
      elimQueues = new ArrayList< ArrayList<Integer> >();
      for(EnzymeGroup eg : getEnzymeGroups()) {
        if (eg.downRegulatedBy != null && !eg.downRegulatedBy.isEmpty()) {
          ArrayList<Integer> elimQueue = new ArrayList<>();
          elimQueues.add(elimQueue);
          actionShuffler.add((Runnable) () -> { handleDownRegulation(eg, elimQueue);  });
        }
      }
    }

    for (SoluteType de : getSoluteTypes()) {
      Double inhfact = Double.NaN;
      if (de.properties.containsKey("elInhibitFactor") && (inhfact = ((Double)de.properties.get("elInhibitFactor"))) > 0.0) {
        elInhibTypes.put(de.tag,inhfact);
      }

    }
      
    // set gsh threshold based on an inverse map to PV-CV position
    gsh_threshold = LinearGradient.eval(GSH_DEPLETION_RANGE.x, GSH_DEPLETION_RANGE.y,0.0,(double)(dist_pv+dist_cv),(double)dist_pv);
    if (!rxnProbMap.isEmpty()) {
      actionShuffler.add(new ReactionHandler((BindingInfo) this, (ReactionInfo) this, cellRNG, log));
    }
    
    // check if ALT is a solute type, and if true use the ALT mechanism 
    // also, add a transporter that does the actual release mechanism
    String alt = "ALT";
    for (SoluteType st : getSoluteTypes()) {
        if (alt.equals(st.tag)) {
            // use ALT mechanism
            useALT = true;
            altST = st;
            // create ALT Transporter
            altTransporter = new Transporter(this);
            // set Solute type for ALT Transporter to the ALT Solute type
            altTransporter.transportedSoluteType = altST;
            // calculate initial membrane damage, should be zero
            init_membrane_damage = altTransporter.getmembDamageCount();
            // set the amount of ALT to the initial ALT amount
            altAmount = initaltAmount;
            // add ALT release mechanism to the actionShuffler
            actionShuffler.add((Runnable) () -> { altRelease(); });
            break;
        }
    }
    
    // suicide action
    actionShuffler.add(new NecrosisHandler(this));

  }
    
  public void handleDownRegulation(EnzymeGroup eg, ArrayList<Integer> elimQueue) {
    ArrayList<String> drb = eg.downRegulatedBy; // to avoid 4 extra f() calls
    if (drb == null || drb.isEmpty()) return;
    
    Map<String,Number> counts = bsg.util.CollectionUtils.countObjectsByType(listSolute());
    
    /**
     * drCapΔ - amount to change EG.Capacity when we execute a DR event or when we replenish instantly 
     */
    int drCapΔ = eg.hasProperty(MetabolicParams.DR_CAP_DELTA)
            ? ((Integer) eg.getProperty(MetabolicParams.DR_CAP_DELTA))
            : MET_ENV.drCapΔ;
    /**
     * drPrΔ - amount to change the prob. of a reaction for that EG
     */
    double drPrΔ = eg.hasProperty(MetabolicParams.DR_PR_DELTA)
            ? ((Double) eg.getProperty(MetabolicParams.DR_PR_DELTA))
            : MET_ENV.drPrΔ;
    // If (1) we lack capacity && (2) the queue is empty && (3) there are no DRing Solutes, there is a chance to replenish
    ////Capacity can only increase by 1 each SCyc

    //If (1) && (2)
    if(eg.getCapacity() < eg.getInitialCapacity() && elimQueue != null && elimQueue.isEmpty()) {
      //Check for (3): whether there is at least one DR causing Solute present
      ///This is done within the current if-statement so no Binomial draw is required if (1) or (2) aren't met.
      boolean drSolutePresent = false;
      double drReplenish = eg.hasProperty(MetabolicParams.DR_REPLENISH)
              ? ((Double)eg.getProperty(MetabolicParams.DR_REPLENISH)) 
              : MET_ENV.drReplenish;
      for (String type : drb) {
        Number n = counts.get(type);
        int count = (n != null ? n.intValue() : 0);
        if (count > 0) {
          drSolutePresent = true;
          int repEvents = myBinomial.nextInt(count,drReplenish);
          if (repEvents > 0) {
            //log.debug("Replenishing H:"+id+"."+eg.type+".Capacity by "+drCapΔ*repEvents);
            eg.changeCapacity(drCapΔ*repEvents); // each event adds DR_CAP_DELTA
            if (rxnProbMapOrig.containsKey(eg)) {
              double ori = rxnProbMapOrig.get(eg);
              double cur = rxnProbMap.get(eg);
              if (cur+drPrΔ <= ori) {
                //log.debug("Raising H:"+id+"."+eg.type+" Pr ("+rxnProbMap.get(eg)+") by "+drPrΔ);
                rxnProbMap.replace(eg,rxnProbMap.get(eg)+drPrΔ);
              } else {
                rxnProbMap.replace(eg,ori);
                rxnProbMapOrig.remove(eg);
              }
            }
          }
        }
      }
      /*
       * Bail if there are no DRing solute and either we're at capacity or 
       * elimQueue is empty.  Note that this could store events in the elimQueue
       * that will be executed if we ever move back below capacity.
       */
      if (!drSolutePresent) return; 
    }
    
    //Pop the queue
    if(elimQueue != null && elimQueue.size() > 0) {
      int numElimEvents = (int) elimQueue.remove(0);
      //log.debug("Popped "+numElimEvents+" to decrement capacity by "+-drCapΔ*numElimEvents+", new elimQueue = "+elimQueue);
      if (numElimEvents > 0) {
        eg.changeCapacity(-drCapΔ*numElimEvents);
        double cur = rxnProbMap.get(eg);
        if (cur > 0.0) {
          if (!rxnProbMapOrig.containsKey(eg)) rxnProbMapOrig.put(eg,cur);
          log.debug("Reducing H:"+id+"."+eg.type+" Pr ("+cur+") by "+drPrΔ);
          double set = cur-drPrΔ;
          if (set < 0.0) set = 0.0;
          rxnProbMap.replace(eg,set);
        }
      }
    }

    //Bail early if there is too much elimination scheduled.
    double elim_interval = (eg.hasProperty(MetabolicParams.DR_INTERVAL) 
            ? ((Double)eg.getProperty(MetabolicParams.DR_INTERVAL))
            : MET_ENV.drInterval);
    if(elimQueue != null && elimQueue.size() > 10*elim_interval) {
      return;
    }
      
    /*
     * For each DRing Solute object, there's a chance to schedule decrease in capacity.
     * If so, it's added to the queue.
     * Capacity can only be scheduled to be reduced by 1 each SCyc.
     */
    double drRemove = eg.hasProperty(MetabolicParams.DR_REMOVE) 
            ? ((Double)eg.getProperty(MetabolicParams.DR_REMOVE)) 
            : MET_ENV.drRemove;
    for (String type : drb) {
      if (counts.containsKey(type)) {
        Number n = counts.get(type);
        int elimEvents = myBinomial.nextInt(n.intValue(), drRemove);
        if(elimQueue == null) elimQueue = new ArrayList<Integer>();

        //log.debug("Adding "+elimEvents+" decrements of "+drCapΔ+" every "+elim_interval+" to elimQueue = "+elimQueue);
        scheduleElimEvents(elimQueue, elimEvents, elim_interval);
        //log.debug(elimEvents+" decrements of "+drCapΔ+" added every "+elim_interval+" to elimQueue = "+elimQueue);
        
      } // end if (counts.containsKey(type)) {
    } // end for (String type : drb) {
    
  } // end handleDownRegulation()

  private void scheduleElimEvents(ArrayList<Integer> q, int total, double interval) {
    int qndx = 0;
    int added = 0;
    double accumulator = 0.0;
    double eventsPerCycle = 1/interval;
    while (added < total) {
      double left = total-added;
      accumulator += (left < eventsPerCycle ? left : eventsPerCycle);
      if (accumulator < 1.0) foldEventsToQueue(q, qndx, 0); 
      else { 
        int acc_i = (int)accumulator;
        foldEventsToQueue(q, qndx, acc_i); 
        accumulator -= acc_i;
        added += acc_i;
      }
      qndx++;
    }
  }
  private void foldEventsToQueue(ArrayList<Integer> q, int ndx, int v) {
    if (ndx < q.size()) q.set(ndx,q.get(ndx)+v);
    else q.add(v);
  }
  
  /*
   * Implementations for CellInfo
   */
  protected int dist_pv = -Integer.MAX_VALUE;
  protected int dist_cv = -Integer.MAX_VALUE;
  @Override
  public int getDPV(boolean actual) { return (actual ? myY+myGrid.ss.priorPathLength : dist_pv); }
  @Override
  public int getDCV(boolean actual) { return (actual ? (myGrid.ss.length-1-myY)+myGrid.ss.postPathLength : dist_cv); }
  @Override
  public double getResources() {
    // ikeys = ["model_time", "distance_from_PV"]
    Object[] ivals = { myGrid.ss.hepStruct.model.getTime(), dist_pv};
    myGrid.ss.hepStruct.rsrc_grad_script.scope.put("ivals", ivals);
    double result = Double.NaN;
    try {
      result = myGrid.ss.hepStruct.rsrc_grad_script.eval();
    } catch (javax.script.ScriptException se) { 
      System.err.println(se.getMessage());
      System.exit(-1);
    }
    return result;
  }
  @Override
  public Map<String,Double> getELInhibTypes() { return elInhibTypes; }
    
  /*
   * Implementations for EIInfo
   */
  @Override
  public int getEIThresh() {return myGrid.ss.ei_thresh;}
  @Override
  public double getEIRate() {return myGrid.ss.ei_rate;}
  @Override
  public double getEIResponse() {return myGrid.ss.ei_response_factor;}
    
  /*
   * Implementations for ELInfo
   */
  @Override
  public int getELThresh() {return myGrid.ss.el_thresh;}
  @Override
  public double getELRate() {return myGrid.ss.el_rate;}
  @Override
  public double getELResponse() {return myGrid.ss.el_response_factor;}
    
  /*
   * Extra Implementations for ReactionInfo
   */
  @Override
  public Map<EnzymeGroup, Double> getRxnProbMap() {return rxnProbMap;}
  @Override
  public Map<EnzymeGroup, Map<String,Double>> getProductionMap() {return productionMap; }
  @Override
  public java.util.ArrayList<SoluteType> getSoluteTypes() {return myGrid.ss.hepStruct.model.delivery.allSolute; }
  
  static double GSH_DEPLETION_INC = Double.NaN;
  static sim.util.Double2D GSH_DEPLETION_RANGE = null;
  public static void setGSHDepletion(sim.util.Double2D range, double inc) {
    GSH_DEPLETION_RANGE = range;
    GSH_DEPLETION_INC = inc;
  }
  
  double gsh_threshold = Double.NaN;
  double gsh_accumulator = 0.0;
  public double getGSHDepletion() { return gsh_accumulator; }

  Transporter altTransporter = null;
  static int initaltAmount = 0;
  static int altThreshold = 0;
  int altAmount = 0;
  public int membrane_damage = 0;
  public int init_membrane_damage = 0;
  SoluteType altST = null;
  public boolean useALT = false;
  public static void setALT(int amount, int threshold) {
      initaltAmount = amount;
      altThreshold = threshold;
  }
  
  public int getALTAmount() { return altAmount; }
  
  public void altRelease(){
    // if ALT solute exists (checked in initialization), then use ALT release mechanism
    if (useALT) {
        //int numnMD = bsg.util.CollectionUtils.countType(this.solutes, "nMD");
        //log.debug("H:"+id+" - number of nMD = "+numnMD);
        membrane_damage = altTransporter.getmembDamageCount(); 
        long cycle = this.myGrid.ss.hepStruct.model.getCycle();
        //log.debug("time: "+cycle+" H:"+id+" - membrane_damage = "+membrane_damage);
        if (membrane_damage > altThreshold) {
            //int nALT = membrane_damage-init_membrane_damage;
            // if membrane damage is > alt threshold, then create an ALT Solute
            //log.debug("at time: "+cycle+" H:"+id+" - nALT = "+nALT);
            if (altAmount > 0) {
                Solute ALT = new Solute(altST.tag, false);
                ALT.setProperties(altST.properties);
                altTransporter.scheduleTransport(ALT);
                altAmount--;
            }  
        } // end of membrane damage checks
        //init_membrane_damage = membrane_damage;
        //log.debug("time: "+cycle+" H:"+id+" - alt_amount = "+altAmount);
    } // end if useALT  
  }
  
  public void transportSolute(Solute s) {
    // transporting the ALT Solute is presenting the Solute to the Hepatocyte
    //log.debug("Hepatocyte:"+id+" transporting Solute at cycle = "+ myGrid.ss.hepStruct.model.getCycle());
    // first present it, then immediately remove it as a leak.
    present(s);
    remove(s, true);
  }

  @Override
  public void add(Solute s) {
    super.add(s);
    myGrid.setObjectLocation(s, myX, myY);
    if (!eliminatedByGSH(s)) bileAndAmpHandling(s);
  } // end public void add(Solute s)

  private boolean eliminatedByGSH(Solute s) {
    boolean retVal = false;
    double accuDec = Double.NaN;
    if (s.hasProperty("gshUp") && (accuDec = ((Double)s.getProperty("gshUp"))) > 0.0 ) {
      if (!s.hasProperty("pGSHUp")) throw new RuntimeException(s.type+" has gshUp but not pGSHUp probability.");
      else {
        double pGSHUp = (Double)s.getProperty("pGSHUp");
        if (cellRNG.nextDouble() < pGSHUp) {
          gsh_accumulator -= accuDec;
          if (gsh_accumulator < 0.0) gsh_accumulator = 0.0;
          forget(s);
          myGrid.ss.gshUpEliminated++;
          retVal = true;
        } 
      }
    }
    return retVal;
  }
  
  private void bileAndAmpHandling(Solute s) {
    if (sendToBile(s)) {
      // remove it from me and myGrid, but not from SS, and count as an exit
      remove(s); myGrid.remove(s);
    } else {
      amplify(s);
    } // end Bile else clause
  }
  
  private boolean sendToBile(Solute s) {
    boolean retVal = false;
    boolean depletesGSH = false;
    Object dgsh = null;
    if ((dgsh = s.getProperty("depletesGSH")) != null) depletesGSH = ((Boolean)dgsh);
    
    // Set bileRatio to zero if GSH threshold is breached
    double br = Double.NaN;
    boolean is_GSH_breached = false;
    if (gsh_accumulator >= gsh_threshold) {
      is_GSH_breached = true;
      br = 0.0;
    } else {
      br = ((Double)s.getProperty("bileRatio"));
    }

    // if draw < bileRatio and there's room, move it to bile
    if (cellRNG.nextDouble() < br && (myGrid.ss.bileCanal.getCC() 
            - myGrid.ss.bileCanal.getTube()[myY].size()) > 1) {
      retVal = true;
      // if a GSH-depleting Solute is added to Bile, increment the accumulator
      if (depletesGSH) gsh_accumulator += GSH_DEPLETION_INC;
      myGrid.ss.bileCanal.getTube()[myY].add(s);
    }
    return retVal;
  }
  
  private void amplify(Solute s) {
      String sType = null;
      sType = s.getType();
      SoluteType st = myGrid.ss.hepStruct.model.delivery.getSoluteType(s);
      boolean amplify = st.isAmplified();
      
      //if ((gsh_accumulator >= gsh_threshold) && amplify) {
      if (amplify) {
        // get random int between 0 and 2 from unifrom distribution
        //int nAmplify = cellRNG.nextInt(3);
        int ampmin = st.ampRange.x;
        int ampmax = st.ampRange.y;
        int nAmplify = ampmin + cellRNG.nextInt(ampmax-ampmin);

        // get random int from a gaussian distribution with mean 10 and std dev 1
        // nextGaussian() produces a double from a gaussian dist with mean 0 and std dev 1
        //double mean = 4;
        //double std = 1;
        //int nAmplify = (int) StrictMath.round(cellRNG.nextGaussian()*std + mean);
        //if (nAmplify < 0) {
        //  nAmplify = StrictMath.abs(nAmplify);
        //}

        // make n copies of amplified solute objects and add to arrays
        Solute ampedSolute = null;
        // SoluteTypes contain the tag, bindable, doseRatio, and props for ampedSolute
        java.util.ArrayList<SoluteType> soluteTypes = this.getSoluteTypes();
        for (int i=0 ; i<nAmplify ; i++) {
          ampedSolute = new Solute(st.tag, st.bindable);
          ampedSolute.setProperties(st.properties);
          // add it to SS's and Cell's solute list
          present(ampedSolute, false);
        } // loop over the amount of ampedSolute amplification
      } // end if (amplify)
  }
  
  @Override // from ReactionHandler
  public void incRxnProd(String st) { myGrid.incRxnProd(st,myX,myY); }
  
  private boolean necrotic = false;
  public void necrose() {
    //log.debug("Hepatocyte:"+id+" dying at cycle = "+ parent.hepStruct.model.getCycle());
    necrotic = true;
    actionShuffler.clear();
    myGrid.ss.necrotic(this);
    //unbind all the bound Solute
    getEnzymeGroups().forEach((eg) -> { eg.boundSolutes.clear(); });
    // bypass official removal because necrosis is different from membrane cross
    solutes.clear();
    // it should be safe to remove myself from the SSGrid because the SS executes
    // me via the SS.cells list in SS.stepBioChem().
    myGrid.getCellGrid().set(myX, myY, null);
  }
  
  public static void main(String[] args ) {
    ec.util.MersenneTwisterFast rng = new ec.util.MersenneTwisterFast(234567890);
    Binomial b = new Binomial(5,0.99,rng);
    for (int trials=0 ; trials<30 ; trials++) {
      int indsuc = 0;
      for (int i=0 ; i<1000 ; i++ ) {
        if (rng.nextDouble() < 0.99) indsuc++;
      }
      int msuc = 0;
      for (int i=0 ; i<1000 ; i++ ) msuc += b.nextInt(1,0.99);
      System.out.println("individual, binomial, mbinomial ⇒ "+indsuc+", "+b.nextInt(1000,0.99)+", "+msuc);
    }
  }
}
