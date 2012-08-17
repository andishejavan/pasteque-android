/*
    POS-Tech Android
    Copyright (C) 2012 SARL SCOP Scil (contact@scil.coop)

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package fr.postech.client.models;

import android.graphics.drawable.Drawable;

import java.io.Serializable;
import org.json.JSONException;
import org.json.JSONObject;

public class Product implements Serializable {

    private String id;
    private String label;
    private double price;
    private String taxId;
    private double taxRate;

    public Product(String id, String label, double price,
                   String taxId,double taxRate) {
        this.id = id;
        this.label = label;
        this.price = price;
        this.taxId = taxId;
        this.taxRate = taxRate;
    }

    public String getId() {
        return this.id;
    }

    public String getLabel() {
        return this.label;
    }

    public double getPrice() {
        return this.price;
    }

    public double getTaxedPrice() {
        return this.price + this.price * this.taxRate;
    }

    public double getTaxPrice() {
        return this.price * this.taxRate;
    }

    public Drawable getIcon() {
        return null;
    }

    public static Product fromJSON(JSONObject o) throws JSONException {
        String id = o.getString("id");
        String label = o.getString("label");
        double price = o.getDouble("price_sell");
        double tax = o.getJSONObject("tax_cat").getJSONObject("taxes").getDouble("rate");
        String taxId = o.getJSONObject("tax_cat").getJSONObject("taxes").getString("id");
        return new Product(id, label, price, taxId, tax);
    }
    
    public JSONObject toJSON() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", this.id);
        o.put("label", this.label);
        o.put("price", this.price);
        o.put("taxId", this.taxId);
        o.put("taxRate", this.taxRate);
        return o;
    }
}