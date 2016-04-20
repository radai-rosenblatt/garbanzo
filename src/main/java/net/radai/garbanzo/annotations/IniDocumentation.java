/*
 * Copyright (c) 2016 Radai Rosenblatt.
 * This file is part of Garbanzo.
 *
 *  Garbanzo is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Garbanzo is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Garbanzo.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.radai.garbanzo.annotations;

import java.lang.annotation.*;

/**
 * Created by Radai Rosenblatt
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
public @interface IniDocumentation {
    String value() default "document me";
}
