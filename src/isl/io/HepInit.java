/*
 * Copyright 2003-2017 - Regents of the University of California, San
 * Francisco.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 */
package isl.io;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class HepInit {
  String description = null;
  Integer rsrc_max_grid = -Integer.MAX_VALUE;
  ArrayList<Integer> layers = new java.util.ArrayList<>();
  int[][] edges = null;
  
  
  public void setDescription(String d) { description = d; }
  public void setNetwork(ArrayList<Integer> z, int[][] e) {
    layers = z; edges = e;
  }
  public void setRsrcMaxGrid(Integer mg) { rsrc_max_grid = mg; }

  public HepInit() {
    hepatocyte_records = new HashMap<Integer,Map<String,Integer>>();
  }
  public void addHepatocyte(Integer id, Integer dPV, Integer dCV) {
    HashMap hm = new HashMap<String,Integer>(3);
    hm.put("dPV",dPV); hm.put("dCV",dCV);
    hepatocyte_records.put(id,hm);
  }
  Map<Integer,Map<String,Integer>> hepatocyte_records = null;
  public int getHepCount() { return hepatocyte_records.size(); }
  public Map<String,Integer> popHepatocyteRecord(int ndx, ec.util.MersenneTwisterFast rng) {
    Map<String, Integer> retVal = hepatocyte_records.get(ndx);
    if (retVal == null) {
      java.util.List<Integer> prkeys = new ArrayList<>(hepatocyte_records.keySet());
      Integer prkey = prkeys.get(rng.nextInt(prkeys.size()));
      retVal = hepatocyte_records.get(prkey);
    }
    return retVal;
  }
  
  public static HepInit readOneOfYou(InputStream is) {
    return Parameters.genson.deserialize(is, HepInit.class);
  }
  
  public String describe() {
    return Parameters.genson.serialize(this);
  }
}
