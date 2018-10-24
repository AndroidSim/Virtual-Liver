/*
 * Copyright 2003-2018 - Regents of the University of California, San
 * Francisco.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package isl.model;

import isl.model.cell.Cell;

public strictfp class DeliveryMethod {
  private static org.slf4j.Logger log = null;
  HepStruct hepStruct = null;  // where to send the dose
  int referenceDose = -1;
  boolean doseRepeats = false;
  int numDoses = -1;
  public java.util.ArrayList<Dose> doses = null;
  public java.util.ArrayList<SoluteType> allSolute = new java.util.ArrayList<>();
  public SoluteType getSoluteType(Solute s) {
    SoluteType st = allSolute.stream().filter((t) -> (t.tag.equals(s.type))).findFirst().get();  
    return st;
  }
  
  /* soluteIn is a map with <Solute.tag, MutableInt> entries */
  java.util.LinkedHashMap<String,Number> soluteIn = new java.util.LinkedHashMap<>();
  
  /**
   * Creates a new instance of DeliveryMethod
   */
  public DeliveryMethod ( HepStruct l ) {
    if ( l != null ) hepStruct = l;
    else throw new RuntimeException( "DeliveryMethod: HepStruct can't be null." );
  }
  public void setLogger(org.slf4j.Logger logger) {
     log = logger;
  }
  public void init ( double maxTime ) {
    ec.util.ParameterDatabase dpd = null, tpd = null;
    try {
      dpd = new ec.util.ParameterDatabase( this.getClass().getClassLoader().getResourceAsStream("cfg/delivery.properties"));
      tpd = new ec.util.ParameterDatabase( this.getClass().getClassLoader().getResourceAsStream("cfg/types.properties"));
    } catch (java.io.IOException ioe) {
      System.err.println( ioe.getMessage() );
      System.exit( -1 );
    }
    DeliveryParams.loadTypeParams( this, tpd );
    
    StringBuilder buff = new StringBuilder("allSolute = {");
    java.util.Iterator<SoluteType> sti = allSolute.iterator();
    do { buff.append(sti.next().tag).append((sti.hasNext()?", ": "")); } while (sti.hasNext());
    log.debug(buff.append("}").toString());
    
    DeliveryParams.loadDoseParams( this, dpd );

    estimateTotal( maxTime );
    
    // initialize the dosage log
    soluteIn.put("Time",0.0);
    for (SoluteType be : allSolute)
      soluteIn.put(be.tag,new bsg.util.MutableInt(0));
  }
  
  public void start( sim.engine.SimState state ) {
    for ( int dNdx=0 ; dNdx<numDoses ; dNdx++ ) {
      Dose d = (doseRepeats ? doses.get(0) : doses.get(dNdx));
      state.schedule.scheduleOnce(hepStruct.model.isl2MasonTime(d.time), ISL.action_order+1, d);
    }
  }

  public long maxSolute = -Integer.MAX_VALUE;
  public java.util.Map<String, bsg.util.MutableInt> maxPerSolute = new java.util.LinkedHashMap<>();
  public void estimateTotal ( double maxTime ) {
    maxSolute = 0;

    /** refMaxSolute -- dynamically constructed solute present a problem with 
     * derived measures, so we hack it to match a bindable and metabolizable 
     * bolus entry.  It is defined as the last bindable, rxnProb>0 bolus solute
     * over all doses.
     */
    bsg.util.MutableInt refMaxSolute = null;
    
    for ( int dNdx=0 ; dNdx<doses.size() ; dNdx++ ) {
      Dose d = doses.get(dNdx);
      long dose_total = 0;
      double stopTime = (d.deliveryType.equals(DeliveryParams.INFUSION_TYPE) ? d.infusionStopTime : maxTime);
      for ( double time = 0.0 ; time < stopTime ; time++ ) {
        dose_total += d.calcDose( time );
      }
      java.util.Map<String,Double> doseEntries = d.getSolution();
      // this calc goes here because each dose can have different solute ratios
      for ( java.util.Map.Entry<String,Double> me : doseEntries.entrySet()) {
         long tmp = StrictMath.round(((double)dose_total)*me.getValue());
         String type = me.getKey();
         if (maxPerSolute.containsKey(type))
            maxPerSolute.get(type).add(tmp);
         else maxPerSolute.put(type, new bsg.util.MutableInt(tmp));
      }
      
      // refMaxSolute is the first Solute type in the first EnzymeGroup that is also dosed
      for (String tag : d.getSolution().keySet()) {
        for (EnzymeGroup eg : Cell.MET_ENV.enzymeGroups.values()) {
          log.debug("Checking for "+tag+" in "+eg.type);
          if (eg.acceptedSolutes.contains(tag) && eg.isProductive()) {
            log.debug("Setting refMaxSolute:  maxPerSolute.get("+tag+") = "+maxPerSolute.get(tag));
            refMaxSolute = maxPerSolute.get(tag);
            break; // break out of the EG loop
          }
        }
        if (refMaxSolute != null) break; // break out of the tag loop
      }
      // if none of the dosed types have a rxnProb, then choose the 1st in the dose
      if (refMaxSolute == null) refMaxSolute = maxPerSolute.get(d.getSolution().keySet().stream().findFirst().get());
      
      // now set the rest of the maxPerSolute to the refMaxSolute
      for ( SoluteType st : allSolute) {
        log.debug("maxPerSolute.get("+st.tag+") = "+ maxPerSolute.get(st.tag));
        if (!maxPerSolute.containsKey(st.tag))
          maxPerSolute.put(st.tag, new bsg.util.MutableInt(refMaxSolute.val));
        else if (maxPerSolute.get(st.tag).val <= 0)
          maxPerSolute.get(st.tag).val = refMaxSolute.val;
      }
      log.debug("Dose."+dNdx+" estimated total = "+dose_total);
      
      maxSolute += dose_total;
    }
    log.debug("estimateTotal("+maxTime+") = "+maxSolute);
  }

  public void registerDose(java.util.Map<String,Number> sm) {
    bsg.util.CollectionUtils.addIn(soluteIn,sm);
    soluteIn.put("Time", hepStruct.model.getTime());
  }
  
  public java.util.Map<String,Number> getSoluteIn() { return soluteIn; }
  public void clearSoluteIn() { 
    soluteIn.remove("Time");
    bsg.util.CollectionUtils.zero_mi(soluteIn); 
    soluteIn.put("Time",Double.NaN);
  }
}
