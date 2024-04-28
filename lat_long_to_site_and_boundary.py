from py_wake.site.xrsite import GlobalWindAtlasSite
import utm
import json
import numpy as np
from scipy.spatial import ConvexHull
import yaml
from pathlib import Path
import os
from pyxtension.Json import Json
from py_wake.examples.data import dtu10mw 
from py_wake.wind_turbines._wind_turbines import WindTurbine
from py_wake.wind_turbines.power_ct_functions import PowerCtTabular
from py_wake import IEA37SimpleBastankhahGaussian
from py_wake import NOJ                                      


def runAEPcalc():
    
    wf_jsonfile = 'C:/Users/Mazlomak/PyWake Environment/NewPyWake/Lib/site-packages/py_wake/examples/WindfarmSimulationData.json'

    with open(wf_jsonfile) as jsonfile:
        data = json.load(jsonfile)

    attr = Json(data)
    # attr.toOrig()
    wtId = []
    wtType_power_norm = []
    wlat_y = [] 
    wlng_x = []
    flag_x = []
    flag_y = []
    wtAltit =[]

    for sub_attr in attr.turbinelist:
        wtId.append(sub_attr.wtId)
        wtType_power_norm.append(sub_attr.wtType)
        wlng_x.append(sub_attr.wtLng)
        wlat_y.append(sub_attr.wtLat)
        wtAltit.append(sub_attr.wtAltitude)

    # print('Original from json: '+str(wlng_x), str(wlat_y))

    for sub_attr in attr.BoundaryFlag:
        flag_x.append(sub_attr.flagLng)
        flag_y.append(sub_attr.flagLat)
    # print('easintg and norhting for Site: '+str(flag_x), str(flag_y))

    easting = np.asarray(flag_x).mean()
    northing = np.asarray(flag_y).mean()
    site = GlobalWindAtlasSite(northing ,easting, height=70, roughness=0.001, ti=0.075)

    # print(easting,northing)

    wt_x_utm=[]
    wt_y_utm=[]
    j=0
    while j<len(wlng_x):
        x, y, _, _ = utm.from_latlon(wlat_y[j], wlng_x[j]) 
        wt_x_utm.append(x)
        wt_y_utm.append(y)
        j+=1

    # print('turbine converted to utm  values: ',  wt_x_utm, wt_y_utm)     

    my_wt = dtu10mw.DTU10WM_RWT()


    # wf_model = IEA37SimpleBastankhahGaussian(site, my_wt)

    # sim_res = wf_model(wt_x_utm,
    #                    wt_y_utm,
    #                    h=None,
    #                    type=0,
    #                    wd=None,
    #                    ws=None )

    noj = NOJ(site, my_wt)
    simulationResult = noj(wt_x_utm, wt_y_utm)
    test =str("Total AEP: %f GWh"%simulationResult.aep().sum())
    
    return test
# simulationResult.aep()

