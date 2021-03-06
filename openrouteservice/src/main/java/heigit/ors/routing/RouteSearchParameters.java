/*
 *  Licensed to GIScience Research Group, Heidelberg University (GIScience)
 *
 *   http://www.giscience.uni-hd.de
 *   http://www.heigit.org
 *
 *  under one or more contributor license agreements. See the NOTICE file 
 *  distributed with this work for additional information regarding copyright 
 *  ownership. The GIScience licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in compliance 
 *  with the License. You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package heigit.ors.routing;

import java.text.ParseException;
import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONObject;

import com.graphhopper.util.Helper;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;

import heigit.ors.exceptions.ParameterValueException;
import heigit.ors.exceptions.UnknownParameterValueException;
import heigit.ors.geojson.GeometryJSON;
import heigit.ors.routing.graphhopper.extensions.HeavyVehicleAttributes;
import heigit.ors.routing.graphhopper.extensions.VehicleLoadCharacteristicsFlags;
import heigit.ors.routing.graphhopper.extensions.WheelchairTypesEncoder;
import heigit.ors.routing.parameters.*;
import heigit.ors.util.StringUtility;

public class RouteSearchParameters {
	private int _profileType;
	private int _weightingMethod = WeightingMethod.FASTEST;
	private Boolean _considerTraffic = false;
	private Boolean _considerTurnRestrictions = false;
	private double _maxSpeed = -1;
	private Polygon[] _avoidAreas;
	private int _avoidFeaturesTypes;
	private int _vehicleType = HeavyVehicleAttributes.UNKNOWN;
	private ProfileParameters _profileParams;
	private WayPointBearing[] _bearings = null;
	private double[] _maxRadiuses;
	private boolean _flexibleMode = false;

	private String _options;

	public int getProfileType() {
		return _profileType;
	}

	public void setProfileType(int profileType) throws Exception {
		if (profileType == RoutingProfileType.UNKNOWN)
			throw new Exception("Routing profile is unknown.");

		this._profileType = profileType;
	}

	public double getMaximumSpeed() {
		return _maxSpeed;
	}

	public void setMaximumSpeed(double maxSpeed) {
		_maxSpeed = maxSpeed;
	}

	public int getWeightingMethod() {
		return _weightingMethod;
	}

	public void setWeightingMethod(int weightingMethod) {
		_weightingMethod = weightingMethod;
	}

	public Boolean getConsiderTraffic() {
		return _considerTraffic;
	}

	public void setConsiderTraffic(Boolean _considerTraffic) {
		this._considerTraffic = _considerTraffic;
	}

	public Polygon[] getAvoidAreas() {
		return _avoidAreas;
	}

	public void setAvoidAreas(Polygon[] avoidAreas) {
		_avoidAreas = avoidAreas;
	}

	public boolean hasAvoidAreas()
	{
		return _avoidAreas != null && _avoidAreas.length > 0;
	}

	public int getAvoidFeatureTypes() {
		return _avoidFeaturesTypes;
	}

	public void setAvoidFeatureTypes(int avoidFeatures) {
		_avoidFeaturesTypes = avoidFeatures;
	}

	public boolean hasAvoidFeatures()
	{
		return  _avoidFeaturesTypes > 0;
	}

	public Boolean getConsiderTurnRestrictions() {
		return _considerTurnRestrictions;
	}

	public void setConsiderTurnRestrictions(Boolean considerTurnRestrictions) {
		_considerTurnRestrictions = considerTurnRestrictions;
	}

	public int getVehicleType() {
		return _vehicleType;
	}

	public void setVehicleType(int vehicleType) {
		this._vehicleType = vehicleType;
	}

	public String getOptions()
	{
		return _options;
	}

	public void setOptions(String options) throws Exception {
		if (options == null)
			return;

		_options = StringUtility.trim(options, '\"');

		//////////////
		// FIXME Only for debugging!!
		// This option for green routing should be constructed by the client
		//		_options = "{\"profile_params\":{\"green_routing\":true}}"
		//////////////

		JSONObject json = null;
		try
		{
			json =	new JSONObject(_options);
		}
		catch(Exception ex)
		{
			throw new ParseException(ex.getMessage(), 0);
		}

		if (json.has("maximum_speed"))
		{
			try
			{
				_maxSpeed = json.getDouble("maximum_speed");
			}
			catch(Exception ex)
			{
				throw new ParameterValueException(RoutingErrorCodes.INVALID_PARAMETER_FORMAT, "maximum_speed", json.getString("maximum_speed"));
			}
		}

		if (json.has("avoid_features"))
		{
			String keyValue = json.getString("avoid_features");
			if (!Helper.isEmpty(keyValue))
			{
				String[]  avoidFeatures = keyValue.split("\\|");
				if (avoidFeatures != null && avoidFeatures.length > 0)
				{
					int flags = 0;
					for (int i = 0; i < avoidFeatures.length; i++)
					{
						String featName = avoidFeatures[i];
						if (featName != null)
						{
							int flag = AvoidFeatureFlags.getFromString(featName);
							if (flag == 0)
								throw new UnknownParameterValueException(RoutingErrorCodes.INVALID_PARAMETER_VALUE, "avoid_features", featName);

							if (!AvoidFeatureFlags.isValid(_profileType, flag, featName))
								throw new ParameterValueException(RoutingErrorCodes.INVALID_PARAMETER_VALUE, "avoid_features", featName);

							flags |= flag;
						}
					}

					if (flags != 0)
						_avoidFeaturesTypes = flags;

				}
			}
		}

		if (json.has("profile_params"))
		{
			JSONObject jProfileParams = json.getJSONObject("profile_params");
			JSONObject jRestrictions = null;

			if (jProfileParams.has("restrictions"))
				jRestrictions = jProfileParams.getJSONObject("restrictions");

			if (RoutingProfileType.isCycling(_profileType))
			{
				CyclingParameters cyclingParams = new CyclingParameters();

				// To make the new API compatible with a new one, we create 'weightings' element.
				/*if (!jProfileParams.has("weightings") && (jProfileParams.has("difficulty_level") || jProfileParams.has("maximum_gradient")))
				{
					JSONObject jWeightings = new JSONObject();

					if (jProfileParams.has("difficulty_level"))
						jWeightings.put("difficulty_level", jProfileParams.get("difficulty_level"));
					else if	(jProfileParams.has("maximum_gradient"))
						jWeightings.put("maximum_gradient", jProfileParams.get("maximum_gradient"));

					jProfileParams.put("weightings", jWeightings);
				}*/

				if (jRestrictions != null)
				{
					if (jRestrictions.has("gradient"))
						cyclingParams.setMaximumGradient(jRestrictions.getInt("gradient"));

					if (jRestrictions.has("trail_difficulty"))
						cyclingParams.setMaximumTrailDifficulty(jRestrictions.getInt("trail_difficulty"));
				}

				_profileParams = cyclingParams;
			}
			else if (RoutingProfileType.isWalking(_profileType)) {
				WalkingParameters walkingParams = new WalkingParameters();

				// To make the new API compatible with a new one, we create 'weightings' element.
				/*if (!jProfileParams.has("weightings") && (jProfileParams.has("difficulty_level") || jProfileParams.has("maximum_gradient")))
				{
					JSONObject jWeightings = new JSONObject();

					if (jProfileParams.has("difficulty_level"))
						jWeightings.put("difficulty_level", jProfileParams.get("difficulty_level"));
					else if	(jProfileParams.has("maximum_gradient"))
						jWeightings.put("maximum_gradient", jProfileParams.get("maximum_gradient"));

					jProfileParams.put("weightings", jWeightings);
				}*/

				if (jRestrictions != null)
				{
					if (jRestrictions.has("gradient"))
						walkingParams.setMaximumGradient(jRestrictions.getInt("gradient"));	

					if (jRestrictions.has("trail_difficulty"))
						walkingParams.setMaximumTrailDifficulty(jRestrictions.getInt("trail_difficulty"));
				}

				_profileParams = walkingParams;
			}
			else if (RoutingProfileType.isHeavyVehicle(_profileType) == true)
			{
				VehicleParameters vehicleParams = new VehicleParameters();

				if (json.has("vehicle_type"))
				{
					String vehicleType = json.getString("vehicle_type");
					_vehicleType =  HeavyVehicleAttributes.getFromString(vehicleType);

					// Since 4.2, all restrictions are packed in its own element
					if (jRestrictions == null)
						jRestrictions = jProfileParams;

					if (jRestrictions.has("length"))
						vehicleParams.setLength(jRestrictions.getDouble("length"));

					if (jRestrictions.has("width"))
						vehicleParams.setWidth(jRestrictions.getDouble("width"));

					if (jRestrictions.has("height"))
						vehicleParams.setHeight(jRestrictions.getDouble("height"));

					if (jRestrictions.has("weight"))
						vehicleParams.setWeight(jRestrictions.getDouble("weight"));

					if (jRestrictions.has("axleload"))
						vehicleParams.setAxleload(jRestrictions.getDouble("axleload"));

					int loadCharacteristics = 0;
					if (jRestrictions.has("hazmat") && jRestrictions.getBoolean("hazmat") == true)
						loadCharacteristics |= VehicleLoadCharacteristicsFlags.HAZMAT;

					if (loadCharacteristics != 0)
						vehicleParams.setLoadCharacteristics(loadCharacteristics);
				}

				_profileParams = vehicleParams;
			}
			else if (_profileType == RoutingProfileType.WHEELCHAIR)
			{
				WheelchairParameters wheelchairParams = new WheelchairParameters();

				// Since 4.2, all restrictions are packed in its own element
				if (jRestrictions == null)
					jRestrictions = jProfileParams;

				if (jRestrictions.has("surface_type"))
					wheelchairParams.setSurfaceType(WheelchairTypesEncoder.getSurfaceType(jRestrictions.getString("surface_type")));

				if (jRestrictions.has("track_type"))
					wheelchairParams.setTrackType(WheelchairTypesEncoder.getTrackType(jRestrictions.getString("track_type")));

				if (jRestrictions.has("smoothness_type"))
					wheelchairParams.setSmoothnessType(WheelchairTypesEncoder.getSmoothnessType(jRestrictions.getString("smoothness_type")));

				if (jRestrictions.has("maximum_sloped_curb"))
					wheelchairParams.setMaximumSlopedCurb((float)jRestrictions.getDouble("maximum_sloped_curb"));

				if (jRestrictions.has("maximum_incline"))
					wheelchairParams.setMaximumIncline((float)jRestrictions.getDouble("maximum_incline"));

				_profileParams = wheelchairParams;
			}

			processWeightings(jProfileParams, _profileParams);
		}

		if (json.has("avoid_polygons"))
		{
			JSONObject jFeature = (JSONObject)json.get("avoid_polygons");

			Geometry geom = null;
			try
			{
				geom = GeometryJSON.parse(jFeature);
			}
			catch(Exception ex)
			{
				throw new ParameterValueException(RoutingErrorCodes.INVALID_JSON_FORMAT, "avoid_polygons");
			}

			if (geom instanceof Polygon)
			{
				_avoidAreas = new Polygon[] { (Polygon)geom };
			}
			else if (geom instanceof MultiPolygon)
			{
				MultiPolygon multiPoly = (MultiPolygon)geom;
				_avoidAreas = new Polygon[multiPoly.getNumGeometries()];
				for (int i = 0; i < multiPoly.getNumGeometries(); i++)
					_avoidAreas[i] = (Polygon)multiPoly.getGeometryN(i);
			}
			else
			{
				throw new ParameterValueException(RoutingErrorCodes.INVALID_PARAMETER_VALUE, "avoid_polygons");
			}
		}
	}

	private void processWeightings(JSONObject json, ProfileParameters profileParams) throws Exception
	{
		if (json != null && json.has("weightings"))
		{
			JSONObject jWeightings = json.getJSONObject("weightings");
			JSONArray jNames = jWeightings.names();

			if (jNames == null)
				return;

			for (int i = 0; i < jNames.length(); i++)
			{
				String name  = jNames.getString(i);
				ProfileWeighting pw = new ProfileWeighting(name);

				JSONObject jw = jWeightings.getJSONObject(name);
				Iterator<String> keys = jw.keys();
				while(keys.hasNext())
				{ 
					String key = keys.next();
					pw.addParameter(key, jw.optString(key));
				}

				profileParams.add(pw);
			}
		}
	}

	public boolean hasParameters(Class<?> value)
	{
		if (_profileParams == null)
			return false;

		return _profileParams.getClass() == value;
	}

	public ProfileParameters getProfileParameters()
	{
		return _profileParams;
	}

	public boolean getFlexibleMode() {
		return _flexibleMode;
	}

	public void setFlexibleMode(boolean flexibleMode) {
		_flexibleMode = flexibleMode;
	}

	public double[] getMaximumRadiuses() {
		return _maxRadiuses;
	}

	public void setMaximumRadiuses(double[] maxRadiuses) {
		_maxRadiuses = maxRadiuses;
	}
	
	public WayPointBearing[] getBearings() {
		return _bearings;
	}

	public void setBearings(WayPointBearing[] bearings) {
		_bearings = bearings;
	}
}
