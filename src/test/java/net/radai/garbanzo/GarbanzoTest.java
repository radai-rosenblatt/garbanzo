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

package net.radai.garbanzo;

import org.apache.commons.lang3.RandomStringUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.*;

/**
 * Created by Radai Rosenblatt
 */
public class GarbanzoTest {

    @Test
    public void testRoundTrip() throws Exception {
        long seed = System.currentTimeMillis();
        Random random = new Random(seed);
        BeanClass original = new BeanClass();
        original.f1 = "";
        original.f2 = RandomStringUtils.randomAscii(10);
        original.f3 = random.nextDouble();
        original.f4 = null;
        original.f5 = UUID.randomUUID();
        original.f6 = new byte[1 + random.nextInt(10)];
        random.nextBytes(original.f6);
        original.f7 = new ArrayList<>();
        for (int i=0; i<5; i++) {
            original.f7.add((long)random.nextInt(10));
        }
        original.f8 = new HashMap<>();
        original.f8.put(Enum1.V1, (short)7);
        original.f8.put(Enum1.V2, null);
        original.f9 = new ArrayList<>();
        for (int i=0; i<3; i++) {
            InnerBeanClass inner = new InnerBeanClass();
            inner.f1 = "bob " + i;
            original.f9.add(inner);
        }
        original.f9.add(null);

        String serialized = Garbanzo.marshal(original);

        BeanClass deserialized = Garbanzo.unmarshall(BeanClass.class, serialized);

        Assert.assertEquals(original, deserialized);
    }

    public enum Enum1 {
        V1, V2
    }

    public static class BeanClass {
        private String f1;
        private String f2;
        private double f3;
        private Integer f4;
        public UUID f5;
        private byte[] f6;
        private List<Long> f7;
        private Map<Enum1, Short> f8;
        private List<InnerBeanClass> f9;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BeanClass beanClass = (BeanClass) o;
            return Double.compare(beanClass.f3, f3) == 0 &&
                    Objects.equals(f1, beanClass.f1) &&
                    Objects.equals(f2, beanClass.f2) &&
                    Objects.equals(f4, beanClass.f4) &&
                    Objects.equals(f5, beanClass.f5) &&
                    Arrays.equals(f6, beanClass.f6) &&
                    Objects.equals(f7, beanClass.f7) &&
                    Objects.equals(f8, beanClass.f8) &&
                    Objects.equals(f9, beanClass.f9);
        }

        @Override
        public int hashCode() {
            return Objects.hash(f1, f2, f3, f4, f5, f6, f7, f8, f9);
        }
    }

    public static class InnerBeanClass {
        private String f1;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            InnerBeanClass that = (InnerBeanClass) o;
            return Objects.equals(f1, that.f1);
        }

        @Override
        public int hashCode() {
            return Objects.hash(f1);
        }
    }
}
