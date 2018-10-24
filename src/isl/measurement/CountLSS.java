/*
 * Copyright 2018 - Regents of the University of California, San
 * Francisco.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package isl.measurement;

import java.util.ArrayList;
import isl.model.ISLParams;
import isl.model.ISL;
import isl.model.SS;

/**
 * CountLSS: Count Location-Specific Solute
 */
public abstract class CountLSS extends Obs {
  
  ISL model = null; // hide the super.model
  ArrayList<String> soluteTypes = null;
  protected boolean fromPV = true;
  
  public CountLSS(ISL tgt, String outputFile, ArrayList<String> stl, boolean pvorcv) {
    super(tgt, outputFile, true);
    model = tgt; // shadow super.model to get access to non-abstract parts
    if (stl != null && !stl.isEmpty()) soluteTypes = stl;
    else throw new RuntimeException(getClass().getSimpleName()+" -- solute types cannot be empty.");
    fromPV = pvorcv; // T→ΣδPV, F→ΣδCV
  }

  protected void init() {
    headers.clear(); // clear those set by Obs
    headers.add("Time");
    // walk the graph to get every valid location
    ArrayList<String> tmpHeaders = new ArrayList<>();
    for (Object ln : model.hepStruct.allNodes) {
      if (ln instanceof SS) {
        SS ss = (SS) ln;
        for (int y=0 ; y<ss.length ; y++) {
          int dist = y+(fromPV ? ss.priorPathLength : ss.postPathLength);
          for (String t : soluteTypes) {
            String column = dist+":"+t;
            if (!tmpHeaders.contains(column)) tmpHeaders.add(dist+":"+t);
          }
        }
      }
    }
    headers.addAll(tmpHeaders);
  }
  
}
