/*
 * Copyright 2003-2018 - Regents of the University of California, San
 * Francisco.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package isl.model.cell;

import isl.model.ISL;
import isl.model.SoluteType;
import isl.model.cell.Hepatocyte;

public class NecrosisHandler implements Runnable {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NecrosisHandler.class );

    Hepatocyte cell = null;  // only Hepatocytes necrose so far
    static sim.util.Double2D necrosisRange = null;
    public static void setNecrosisRange(sim.util.Double2D range) { necrosisRange = range; }

    // preliminary stub for necrosis tipping point hypothesis
    private final double DIGESTED_MATERIAL = 1.0;
    // Solute types that have the "causesNecrosis" property
    static java.util.ArrayList<String> necrosisTypes = null;
    static java.util.ArrayList<String> determineNecrosisTypes(java.util.ArrayList<SoluteType> bolusEntries) {
      java.util.ArrayList<String> nt = new java.util.ArrayList<>();
      for (SoluteType be : bolusEntries) {
        if (be.properties.containsKey("causesNecrosis") && ((Boolean)be.properties.get("causesNecrosis"))) nt.add(be.tag);
      }
      return nt;
    }
    // Solute types with the "inhibitsNecrosis" property
    static java.util.Map<String,Number> inhTypes = null;
    static java.util.Map<String,Number> determineNecrosisInhTypes(java.util.ArrayList<SoluteType> bolusEntries) {
      java.util.HashMap<String,Number> it = new java.util.HashMap<>();
      for (SoluteType be : bolusEntries) {
        if (be.properties.containsKey("inhibitsNecrosis") && ((Boolean)be.properties.get("inhibitsNecrosis"))) {
          if (be.properties.containsKey("inhibitionPotency")) 
            it.put(be.tag, ((Double)be.properties.get("inhibitionPotency")));
          else
            throw new RuntimeException(be.tag+" inhibitsNecrosis but has no associated potency.");
        }
      }
      return it;
    }
    
    public static int NECROSIS_DELAY_MIN = Integer.MAX_VALUE;
    public static int NECROSIS_DELAY_MAX = -Integer.MAX_VALUE;
    double necrosis_thresh = Double.NaN;
    sim.engine.TentativeStep necrosis_stop = null;
    
    public NecrosisHandler(Hepatocyte c) {
        cell = c;
        // set the necrosis threshold based on an inverse map PV-CV position
        int dist_pv = cell.getDPV(false);
        necrosis_thresh = bsg.util.LinearGradient.eval(necrosisRange.x, necrosisRange.y, 0.0, (double)(dist_pv+cell.getDCV(false)),(double)dist_pv);
        java.util.ArrayList<SoluteType> be = cell.getSoluteTypes();
        if (necrosisTypes == null) 
          necrosisTypes = determineNecrosisTypes(cell.getSoluteTypes());
        if (inhTypes == null)
          inhTypes = determineNecrosisInhTypes(be);
    }
    private int getNecrosisCount() {
      int sum = 0;
      for (String t : necrosisTypes)
        sum += bsg.util.CollectionUtils.countObjectsOfType(cell.listSolute(), t);
      return sum;
    }
    /**
     * @param types A Map from inhibiting type => inhibition potency
     * @return null || a Map from |inhibiting type| => inhibition potency
     */
    private java.util.Map<Number,Number> getInhibitorCount() { 
      java.util.Map<Number,Number> retVal = null;
      int sum = 0;
      for (java.util.Map.Entry<String,Number> me : inhTypes.entrySet()) {
        sum += bsg.util.CollectionUtils.countObjectsOfType(cell.listSolute(),me.getKey());
        if (sum > 0) {
          if (retVal == null) retVal = new java.util.HashMap<>(1);
          retVal.put(sum, me.getValue());
        }
      }
      return retVal;
    }

    @Override
    public void run() {
      // test to see if my cell's necrosis causing solute is > necrosis_thresh
      int deadly_count = getNecrosisCount();
      if (deadly_count > necrosis_thresh && necrosis_stop == null) { scheduleNecrosis(); }
      if (necrosis_stop != null) {
        java.util.Map<Number, Number> inh_counts = getInhibitorCount();
        if (inh_counts != null && !inh_counts.isEmpty()) {
          double numerator = 0; // ∑(|inhibitor|*potency)
          for (java.util.Map.Entry<Number, Number> me : inh_counts.entrySet()) {
            numerator += me.getKey().doubleValue() * me.getValue().doubleValue();
          }
          double nec_inh_prob = numerator / (getNecrosisCount() + DIGESTED_MATERIAL);
          if (cell.cellRNG.nextDouble() <= nec_inh_prob) {
            //log.debug("NecrosisHandler.run() cycle = "+cell.parent.hepStruct.model.getCycle()+", removing necrosis event for cell " + cell.id);
            necrosis_stop.stop();  // remove it from the schedule
            necrosis_stop = null;  // forget it
          }
        }

      }
    }
    
    private void scheduleNecrosis() {
      long cycle = cell.myGrid.ss.hepStruct.model.getCycle();

      // Beta distribution
      //isl.util.PRNGWrapper prngw = new isl.util.PRNGWrapper(cell.cellRNG);
      //double beta_a = 2;
      //double beta_b = 2;
      //cern.jet.random.Beta betaDist = new cern.jet.random.Beta(beta_a, beta_b, prngw);
      //long sched = (long)(NECROSIS_DELAY_MIN + betaDist.nextDouble()*(NECROSIS_DELAY_MAX - NECROSIS_DELAY_MIN));
      
      // Gaussian distribution:
      //double mean = (NECROSIS_DELAY_MIN + NECROSIS_DELAY_MAX) / 2;
      //double std = 3024;
      //long sched = (long) (cell.cellRNG.nextGaussian() * std + mean);
      //if (sched < 0) {
      //  sched = StrictMath.abs(sched);
      //}

      //log.debug("NecrosisHandler.scheduleNecrosis() for cell "+cell.id+" at cycle "+Long.toString(cycle+sched));

      // Uniform distribution:
      long sched = NECROSIS_DELAY_MIN + cell.cellRNG.nextInt(NECROSIS_DELAY_MAX-NECROSIS_DELAY_MIN);
      //log.debug("Scheduling cell.necrose() at cycle = "+(cycle+sched)+", getTime() => "+cell.parent.hepStruct.model.parent.schedule.getTime());
      
      sim.engine.Steppable necrosis_step = (sim.engine.SimState state) -> { necrosisEvent(); };
      sim.engine.Schedule schedule = cell.myGrid.ss.hepStruct.model.parent.schedule;
      necrosis_stop = new sim.engine.TentativeStep(necrosis_step);
      boolean success = schedule.scheduleOnce(cycle + sched, ISL.action_order+1, necrosis_stop);
      if (success) cell.myGrid.ss.necTrig(cell);
      if (!success) throw new RuntimeException("Failed to schedule a necrosis event for cell "+cell.id);

      //log.debug("NecrosisHandler:"+cell.id+".scheduleNecrosis() at "+cycle+" for "+cycle+sched);
    } // end scheduleNecrosis();
  
    void necrosisEvent() {
      //log.debug("NecrosisHandler:"+cell.id+".necrosisEvent()");
      cell.necrose();
    }
}
