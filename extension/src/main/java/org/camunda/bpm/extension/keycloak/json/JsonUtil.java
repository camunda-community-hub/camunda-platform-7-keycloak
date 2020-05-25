package org.camunda.bpm.extension.keycloak.json;

import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Utility class for JSON parsing / streaming functions.
 */
public class JsonUtil {

	/**
	 * Parses a given JSON String as JsonObject.
	 * @param jsonString the JSON string
	 * @return the JsonObject
	 * @throws JsonException in case of errors
	 */
	public static JsonObject parseAsJsonObject(String jsonString) throws JsonException {
		try {
			return JsonParser.parseString(jsonString).getAsJsonObject();
		} catch (JsonParseException | IllegalStateException ex) {
			throw new JsonException("Unable to parse as JsonObject: " + jsonString, ex);
		}
	}

	/**
	 * Parses a given JSON String as JsonArray.
	 * @param jsonString the JSON string
	 * @return the JsonArray
	 * @throws JsonException in case of errors
	 */ 
	public static JsonArray parseAsJsonArray(String jsonString) throws JsonException {
		try {
			return JsonParser.parseString(jsonString).getAsJsonArray();
		} catch (JsonParseException | IllegalStateException ex) {
			throw new JsonException("Unable to parse as JsonArray: " + jsonString, ex);
		}
	}

	/**
	 * Parses a given JSON String as JsonObject and returns the String value of one of it's members.
	 * @param jsonString the JSON string
	 * @param memberName the name of the member to return
	 * @return the String value of the member or {@code null} if no such member exists
	 * @throws JsonException in case of errors
	 */
	public static String parseAsJsonObjectAndGetMemberAsString(String jsonString, String memberName) throws JsonException {
		return getJsonString(parseAsJsonObject(jsonString), memberName);
	}
	
	/**
	 * Returns the String value of a member of a given JsonObject.
	 * @param jsonObject the JsonObject
	 * @param memberName the name of the member to return
	 * @return the String value of the member or {@code null} if no such member exists
	 * @throws JsonException in case of errors
	 */
	public static String getJsonString(JsonObject jsonObject, String memberName) throws JsonException {
		try {
			JsonElement element = jsonObject.get(memberName);
			return element == null ? null : element.getAsString();
		} catch (ClassCastException | IllegalStateException ex) {
			throw new JsonException("Unable to get '" + memberName + "' from JsonObject " + jsonObject.toString(), ex);
		}
	}

	/**
	 * Returns the optional String value of a member of a given JsonObject.
	 * @param jsonObject the JsonObject
	 * @param memberName the name of the member to return
	 * @return the String value of the member or the empty string {@code ""} if no such member exists
	 * @throws JsonException in case of errors
	 */
	public static String getOptJsonString(JsonObject jsonObject, String memberName) throws JsonException {
		try {
			JsonElement element = jsonObject.get(memberName);
			return element == null ? "" : element.getAsString();
		} catch (ClassCastException | IllegalStateException ex) {
			throw new JsonException("Unable to get '" + memberName + "' from JsonObject " + jsonObject.toString(), ex);
		}
	}

	/**
	 * Returns the Long value of a member of a given JsonObject.
	 * @param jsonObject the JsonObject
	 * @param memberName the name of the member to return
	 * @return the Long value of the member or {@code null} if no such member exists
	 * @throws JsonException in case of errors
	 */
	public static Long getJsonLong(JsonObject jsonObject, String memberName) throws JsonException {
		try {
			JsonElement element = jsonObject.get(memberName);
			return element == null ? null : element.getAsLong();
		} catch (ClassCastException | IllegalStateException ex) {
			throw new JsonException("Unable to get '" + memberName + "' from JsonObject " + jsonObject.toString(), ex);
		}
	}

	/**
	 * Returns a specific member of a given JsonObject as JsonObject.
	 * @param jsonObject the JsonObject
	 * @param memberName the name of the member to return
	 * @return the JsonObject value of the member or {@code null} if no such member exists
	 * @throws JsonException in case of errors
	 */
	public static JsonObject getJsonObject(JsonObject jsonObject, String memberName) throws JsonException {
		if (jsonObject == null) return null;
		try {
			JsonElement element = jsonObject.get(memberName);
			return element == null ? null : element.getAsJsonObject();
		} catch (ClassCastException | IllegalStateException ex) {
			throw new JsonException("Unable to get '" + memberName + "' from JsonObject " + jsonObject.toString(), ex);
		}
	}

	/**
	 * Returns a specific member of a given JsonObject as JsonArray.
	 * @param jsonObject the JsonObject
	 * @param memberName the name of the member to return
	 * @return the JsonArray value of the member or {@code null} if no such member exists
	 * @throws JsonException in case of errors
	 */
	public static JsonArray getJsonArray(JsonObject jsonObject, String memberName) throws JsonException {
		if (jsonObject == null) return new JsonArray();
		try {
			JsonElement element = jsonObject.get(memberName);
			return element == null ? new JsonArray() : element.getAsJsonArray();
		} catch (ClassCastException | IllegalStateException ex) {
			throw new JsonException("Unable to get '" + memberName + "' from JsonObject " + jsonObject.toString(), ex);
		}
	}

	/**
	 * Returns the JsonObject at a specific index from a given JsonArray.
	 * @param jsonArray the JsonArray
	 * @param i the index
	 * @return the JsonObject at the given index
	 * @throws JsonException in case of errors including the case that no element exists at the specified index
	 */
	public static JsonObject getJsonObjectAtIndex(JsonArray jsonArray, int i) throws JsonException {
		try {
			return jsonArray.get(i).getAsJsonObject();
		} catch (IndexOutOfBoundsException | IllegalStateException ex) {
			throw new JsonException("Unable to get index " + i + " from JsonArray " + jsonArray.toString(), ex);
		}
	}
	
	/**
	 * Returns the String value at a specific index from a given JsonArray.
	 * @param jsonArray the JsonArray
	 * @param i the index
	 * @return the String value at the given index
	 * @throws JsonException in case of errors including the case that no element exists at the specified index
	 */
	public static String getJsonStringAtIndex(JsonArray jsonArray, int i) throws JsonException {
		try {
			return jsonArray.get(i).getAsString();
		} catch (IndexOutOfBoundsException | ClassCastException | IllegalStateException ex) {
			throw new JsonException("Unable to get index " + i + " from JsonArray " + jsonArray.toString(), ex);
		}
	}

	/**
	 * Finds the first element in a JsonArray list, where a given attribute matches a given name.
	 * @param list the JsonArray list
	 * @param attributeToMatch the name of the JsonObject attribute to match against
	 * @param attributeValue the value of the attribute
	 * @return matching JsonObject element if found, {@code null} otherwise
	 * @throws JsonException in case of errors
	 */
	public static JsonObject findFirst(JsonArray list, String attributeToMatch, String attributeValue) throws JsonException {
		for (int i=0; i < list.size(); i++) {
			JsonObject result = list.get(i).getAsJsonObject();
			if (attributeValue.equalsIgnoreCase(getJsonString(result, attributeToMatch))) {
				return result;
			}
		}
		return null;
	}

}
