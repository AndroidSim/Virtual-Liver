/* 
 * Copyright 2012-2016 - Regents of the University of California, San
 * Francisco.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

//
// local parameters
// 
//  yrange_modifier reduces the overall slope by reducing the y intercept
//  from the value of ei_gradient_yrange to ~80% of that at the end of sim
var ei_gradient_yrange_target = 0.8; // value of yrange at end of sim
var ei_gradient_yrange_start = 1.0;
//# gradient is ~ f(eiGradShape/max_grid * path length from PV)
//# i.e. it's related to the alpha/beta for the SS lengths
var ei_gradient_shape = 5.6;
var ei_gradient_asymptote = 0.0;

// expected parameter map
var pkeys, pvals;
var pMap = {};
pMap["max_time"] = Java.type("java.lang.Double");
pMap["max_length"] = Java.type("java.lang.Double");

// expected input map
var ikeys, ivals;
var iMap = {};
iMap["model_time"] = Java.type("java.lang.Double");
iMap["distance_from_PV"] = Java.type("java.lang.Integer");

function eval() {
  check(pkeys,pvals, pMap);
  var max_time = pvals[0];
  var max_length = pvals[1];
  
  check(ikeys, ivals, iMap);
  var model_time = ivals[0]; // model (ISL) time passed from Java
  var distance_from_PV = ivals[1]; // grid points passed in from Java

  var effective_yrange = ei_gradient_yrange_start 
      - model_time/max_time * (ei_gradient_yrange_start - ei_gradient_yrange_target);

  var y = effective_yrange
  / Math.exp((ei_gradient_shape/max_length)
          * distance_from_PV) 
          + ei_gradient_asymptote;
  return y;
}
