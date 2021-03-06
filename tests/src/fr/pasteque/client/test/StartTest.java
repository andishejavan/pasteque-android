/*
    Pasteque Android client
    Copyright (C) Pasteque contributors, see the COPYRIGHT file

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

package fr.pasteque.client.test;

import fr.pasteque.client.Start;
import android.test.ActivityInstrumentationTestCase2;

public class StartTest extends ActivityInstrumentationTestCase2<Start> {

    private Start activity;
    
    public StartTest() {
        super("fr.pasteque.client", Start.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        this.activity = getActivity();
        
    }
    
    public void testBasic() {
        assertTrue(true);
    }
}