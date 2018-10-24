/*
 * Copyright 2017-2018 - Regents of the University of California, San
 * Francisco.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */
package isl.measurement;

import bsg.util.CollectionUtils;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import isl.model.ISL;
import isl.model.HepStruct;
import isl.model.cell.Cell;
import isl.model.Solute;
import isl.model.SS;
import isl.model.Vas;

public class CountExtraCellularSolute extends Obs {
  HepStruct hepStruct = null;
  public CountExtraCellularSolute(ISL tgt, String f, boolean z) {
    super(tgt,f,z);
    hepStruct = tgt.hepStruct;
  }
  
  @Override
  public void step(sim.engine.SimState state) {
    Set<Solute> allS = new HashSet<>();
    Set<Solute> intraS = new HashSet<>();
    Map<String,Number> vasaC = new HashMap<>();
    for (Object lno : hepStruct.allNodes) {
      if (lno instanceof Vas) {
        Vas v = (Vas)lno;
        CollectionUtils.addIn(vasaC, CollectionUtils.deepCopy(v.getSoluteMap()));
        Map<String,Number> p = v.getPassed();
        if (p != null) CollectionUtils.addIn(vasaC, p);
      } else {
        SS ss = (SS)lno;
        allS.addAll(ss.solutes);
        for (Object co : ss.cells) {
          intraS.addAll(((Cell)co).listSolute());
        }
      }
    }
    allS.removeAll(intraS);
    Map<String,Number> extraC = CollectionUtils.countObjectsByType(new ArrayList<>(allS));
    CollectionUtils.addIn(extraC, vasaC);
    extraC.put("Time", model.getTime());
    writeOutputs(extraC);
  }
}
