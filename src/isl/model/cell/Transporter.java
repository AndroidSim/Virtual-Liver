/*
 * Transporter.java
 *
 * Created on July 9, 2007, 12:25 PM
 *
 * Moves solute into/outof Cells.
 *
 * Copyright 2003-2017 - Regents of the University of California, San
 * Francisco.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package isl.model.cell;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import bsg.util.CollectionUtils;
import bsg.util.LinearGradient;
import bsg.util.SigmoidGradient;
import isl.model.SoluteType;
import isl.model.Solute;
import isl.model.ISL;
import isl.model.Solute;
import isl.model.SoluteType;

/**
 *
 * @author gepr, aks(2018)
 */
public class Transporter {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NecrosisHandler.class );
    
    public Hepatocyte cell = null;  // only Hepatocytes transport Solutes for now
    public SoluteType transportedSoluteType = null;
    //public Solute transportedSolute = null;
    //public long transportTime = 5000;
    public static int TRANSPORT_DELAY_MIN = Integer.MAX_VALUE;
    public static int TRANSPORT_DELAY_MAX = -Integer.MAX_VALUE;
    
    /** Creates a new instance of Transporter */
    public Transporter(Hepatocyte c) {
        cell = c;
        java.util.ArrayList<SoluteType> be = cell.getSoluteTypes();
        if (membDamageTypes == null) { 
            membDamageTypes = determineMembDamageTypes(cell.getSoluteTypes());
        }
    }
    
    // Solute types that have the "membraneDamage" property
    public java.util.ArrayList<String> membDamageTypes = null;
    public java.util.ArrayList<String> determineMembDamageTypes(java.util.ArrayList<SoluteType> bolusEntries) {
      java.util.ArrayList<String> mdt = new java.util.ArrayList<>();
      for (SoluteType be : bolusEntries) {
        if (be.properties.containsKey("membraneDamage") && ((Boolean)be.properties.get("membraneDamage"))) mdt.add(be.tag);
      }
      return mdt;
    }
    
    public int getmembDamageCount() {
      int sum = 0;
      for (String t : membDamageTypes)
        sum += bsg.util.CollectionUtils.countObjectsOfType(cell.solutes, t);
      return sum;
    }
    
    public void scheduleTransport(Solute s) {
      long cycle = cell.myGrid.ss.hepStruct.model.getCycle();
      //log.debug("Transporter.scheduleTransport() for cell "+cell.id+" at cycle "+Long.toString(cycle));

      // Uniform distribution:
      long sched = 0;
      if (TRANSPORT_DELAY_MAX == TRANSPORT_DELAY_MIN) {
          sched = TRANSPORT_DELAY_MIN;
      } else {
          sched = TRANSPORT_DELAY_MIN + cell.cellRNG.nextInt(TRANSPORT_DELAY_MAX-TRANSPORT_DELAY_MIN);
      }
      //log.debug("Scheduling cell.transportSolute(Solute s) at cycle = "+(cycle)+", getTime() => "+cell.myGrid.ss.hepStruct.model.parent.schedule.getTime());
      
      sim.engine.Steppable transport_step = (sim.engine.SimState state) -> { transportEvent(s); };
      sim.engine.Schedule schedule = cell.myGrid.ss.hepStruct.model.parent.schedule;
      //necrosis_stop = new sim.engine.TentativeStep(necrosis_step);
      boolean success = schedule.scheduleOnce(cycle + sched, ISL.action_order+1, transport_step);
      //if (success) cell.myGrid.ss.necTrig(cell);
      if (!success) throw new RuntimeException("Failed to schedule a transportSolute event for cell "+cell.id);

      //log.debug("Transporter:"+cell.id+".scheduleTransport() at "+cycle+" for "+(cycle + sched));
    } // end scheduleNecrosis();
    
    public void transportEvent(Solute s) {
      //log.debug("TransportHandler:"+cell.id+".transportEvent()");
      cell.transportSolute(s);
    }
}
