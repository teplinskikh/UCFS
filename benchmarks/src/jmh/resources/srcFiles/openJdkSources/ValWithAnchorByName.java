/*
 * Copyright (c) 2004, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 8132926
 * @summary PKIXParameters built with public key form of TrustAnchor causes
 *          NPE during cert path building/validation
 * @run main ValWithAnchorByName
 */

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.PKIXParameters;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;


public class ValWithAnchorByName {


    private static final String EE_CERT =
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIDnTCCAoWgAwIBAgICEAAwDQYJKoZIhvcNAQELBQAwNTEUMBIGA1UEChMLU29t\n" +
        "ZUNvbXBhbnkxHTAbBgNVBAMTFEludGVybWVkaWF0ZSBDQSBDZXJ0MB4XDTE2MDgz\n" +
        "MDIxMzcxOVoXDTE3MDgzMDIxMzcxOVowLzEUMBIGA1UEChMLU29tZUNvbXBhbnkx\n" +
        "FzAVBgNVBAMTDlNTTENlcnRpZmljYXRlMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A\n" +
        "MIIBCgKCAQEAjgv8KKE4CO0rbCjRLA1hXjRiSq30jeusCJ8frbRG+QOBgQ3j6jgc\n" +
        "vk5wG1aTu7R4AFn0/HRDMzP9ZbRlZVIbJUTd8YiaNyZeyWapPnxHWrPCd5e1xopk\n" +
        "ElieDdEH5FiLGtIrWy56CGA1hfQb1vUVYegyeY+TTtMFVHt0PrmMk4ZRgj/GtVNp\n" +
        "BQQYIzaYAcrcWMeCn30ZrhaGAL1hsdgmEVV1wsTD4JeNMSwLwMYem7fg8ondGZIR\n" +
        "kZuGtuSdOHu4Xz+mgDNXTeX/Bp/dQFucxCG+FOOM9Hoz72RY2W8YqgL38RlnwYWp\n" +
        "nUNxhXWFH6vyINRQVEu3IgahR6HXjxM7LwIDAQABo4G8MIG5MBQGA1UdEQQNMAuC\n" +
        "CWxvY2FsaG9zdDAyBggrBgEFBQcBAQQmMCQwIgYIKwYBBQUHMAGGFmh0dHA6Ly9s\n" +
        "b2NhbGhvc3Q6NDIzMzMwHwYDVR0jBBgwFoAUYT525lwHCI4CmuWs8a7poaeKRJ4w\n" +
        "HQYDVR0OBBYEFCaQnOX4L1ovqyfeKuoay+kI+lXgMA4GA1UdDwEB/wQEAwIFoDAd\n" +
        "BgNVHSUEFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIwDQYJKoZIhvcNAQELBQADggEB\n" +
        "AD8dqQIqFasJcL8lm4mPTsBl0JgNiN8tQcXM7VCvcH+yDvEyh9vudDjuhpSORqPq\n" +
        "f1o/EvJ+gfs269mBnYQujYRvmSd6EAcBntv5zn6amOh03o6PqTY9KaUC/mL9hB84\n" +
        "Y5/LYioP16sME7egKnlrGUgKh0ZvGzm7c3SYx3Z5YoeFBOkZajc7Jm+cBw/uBQkF\n" +
        "a9mLEczIvOgkq1wto8vr2ptH1gEuvFRcorN3muvq34bk40G08+AHlP3fCLFpI3FA\n" +
        "IStJLJZRcO+Ib4sOcKuaBGnuMo/QVOCEMDUs6RgiWtSd93OZKFIUOASVp6YIkcSs\n" +
        "5/rmc06sICqBjLfPEB68Jjw=\n" +
        "-----END CERTIFICATE-----";

    private static final String INT_CA_CERT =
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIDdjCCAl6gAwIBAgIBZDANBgkqhkiG9w0BAQsFADAtMRQwEgYDVQQKEwtTb21l\n" +
        "Q29tcGFueTEVMBMGA1UEAxMMUm9vdCBDQSBDZXJ0MB4XDTE2MDgwNzIxMzcxOVoX\n" +
        "DTE4MDgwNzIxMzcxOVowNTEUMBIGA1UEChMLU29tZUNvbXBhbnkxHTAbBgNVBAMT\n" +
        "FEludGVybWVkaWF0ZSBDQSBDZXJ0MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIB\n" +
        "CgKCAQEAnJR5CnE7GKlQjigExSJ6hHu302mc0PcA6TDgsIitPYD/r8RBbBuE51OQ\n" +
        "7IP7AXmfPUV3/+pO/uxx6mgY5O6XeUl7KadhVPtPcL0BVVevCSOdTMVa3iV4zRpa\n" +
        "C6Uy2ouUFnafKnDtlbieggyETUoNgVNJYA9L0XNhtSnENoLHC4Pq0v8OsNtsOWFR\n" +
        "NiMTOA49NNDBw85WgPyFAxjqO4z0J0zxdWq3W4rSMB8xrkulv2Rvj3GcfYJK/ab8\n" +
        "V1IJ6PMWCpujASY3BzvYPnN7BKuBjbWJPgZdPYfX1cxeG80u0tOuMfWWiNONSMSA\n" +
        "7m9y304QA0gKqlrFFn9U4hU89kv1IwIDAQABo4GYMIGVMA8GA1UdEwEB/wQFMAMB\n" +
        "Af8wMgYIKwYBBQUHAQEEJjAkMCIGCCsGAQUFBzABhhZodHRwOi8vbG9jYWxob3N0\n" +
        "OjM5MTM0MB8GA1UdIwQYMBaAFJNMsejEyJUB9tiWycVczvpiMVQZMB0GA1UdDgQW\n" +
        "BBRhPnbmXAcIjgKa5azxrumhp4pEnjAOBgNVHQ8BAf8EBAMCAYYwDQYJKoZIhvcN\n" +
        "AQELBQADggEBAE4nOFdW9OirPnRvxihQXYL9CXLuGQz5tr0XgN8wSY6Un9b6CRiK\n" +
        "7obgIGimVdhvUC1qdRcwJqgOfJ2/jR5/5Qo0TVp+ww4dHNdUoj73tagJ7jTu0ZMz\n" +
        "5Zdp0uwd4RD/syvTeVcbPc3m4awtgEvRgzpDMcSeKPZWInlo7fbnowKSAUAfO8de\n" +
        "0cDkxEBkzPIzGNu256cdLZOqOK9wLJ9mQ0zKgi/2NsldNc2pl/6jkGpA6uL5lJsm\n" +
        "fo9sDusWNHV1YggqjDQ19hrf40VuuC9GFl/qAW3marMuEzY/NiKVUxty1q1s48SO\n" +
        "g5LoEPDDkbygOt7ICL3HYG1VufhC1Q2YY9c=\n" +
        "-----END CERTIFICATE-----";

    private static final String ROOT_CA_CERT =
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIDODCCAiCgAwIBAgIBATANBgkqhkiG9w0BAQsFADAtMRQwEgYDVQQKEwtTb21l\n" +
        "Q29tcGFueTEVMBMGA1UEAxMMUm9vdCBDQSBDZXJ0MB4XDTE2MDcwODIxMzcxOFoX\n" +
        "DTE5MDYyODIxMzcxOFowLTEUMBIGA1UEChMLU29tZUNvbXBhbnkxFTATBgNVBAMT\n" +
        "DFJvb3QgQ0EgQ2VydDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAIlN\n" +
        "M3WYEqkU2elXEZrV9QSDbDKwyaLEHafLFciH8Edoag3q/7jEzFJxI7JZ831tdbWQ\n" +
        "Bm6Hgo+8pvetOFW1BckL8eIjyOONP2CKfFaeMaozsWi1cgxa+rjpU/Rekc+zBqvv\n" +
        "y4Sr97TwT6nQiLlgjC1nCfR1SVpO51qoDChS7n785rsKEZxw/p+kkVWSZffU7zN9\n" +
        "c645cPg
        "Tw84Rr4zlUEQBgXzQlRt+mPzeaDpdG1EeGkXrcdkZ+0EMELoOVXOEn6VNsz6vT3I\n" +
        "KrnvQBSnN06xq/iWwC0CAwEAAaNjMGEwDwYDVR0TAQH/BAUwAwEB/zAfBgNVHSME\n" +
        "GDAWgBSTTLHoxMiVAfbYlsnFXM76YjFUGTAdBgNVHQ4EFgQUk0yx6MTIlQH22JbJ\n" +
        "xVzO+mIxVBkwDgYDVR0PAQH/BAQDAgGGMA0GCSqGSIb3DQEBCwUAA4IBAQAAi+Nl\n" +
        "sxP9t2IhiZIHRJGSBZuQlXIjwYIwbq3ZWc/ApZ+0oxtl7DYQi5uRNt8/opcGNCHc\n" +
        "IY0fG93SbkDubXbxPYBW6D/RUjbz59ZryaP5ym55p1MjHTOqy+AM8g41xNTJikc3\n" +
        "UUFXXnckeFbawijCsb7vf71owzKuxgBXi9n1rmXXtncKoA/LrUVXoUlKefdgDnsU\n" +
        "sl3Q29eibE3HSqziMMoAOLm0jjekFGWIgLeTtyRYR1d0dNaUwsHTrQpPjxxUTn1x\n" +
        "sAPpXKfzPnsYAZeeiaaE75GwbWlHzrNinvxdZQd0zctpfBJfVqD/+lWANlw+rOaK\n" +
        "J2GyCaJINsyaI/I2\n" +
        "-----END CERTIFICATE-----";

    private static final String EE_OCSP_RESP =
        "MIIFbAoBAKCCBWUwggVhBgkrBgEFBQcwAQEEggVSMIIFTjCBtaE3MDUxFDASBgNV\n" +
        "BAoTC1NvbWVDb21wYW55MR0wGwYDVQQDExRJbnRlcm1lZGlhdGUgQ0EgQ2VydBgP\n" +
        "MjAxNjA5MDYyMTM3MjBaMGUwYzA7MAkGBSsOAwIaBQAEFH7SPUOWFS6rfQxK2MHK\n" +
        "FBiqBd1UBBRhPnbmXAcIjgKa5azxrumhp4pEngICEACAABgPMjAxNjA5MDYyMTM3\n" +
        "MjBaoBEYDzIwMTYwOTA2MjIzNzE5WqECMAAwDQYJKoZIhvcNAQELBQADggEBAF13\n" +
        "cLwxDG8UYPIbzID86vZGOWUuv5c35VnvebMk/ajAUdpItDYshIQVi90Z8BB2TEi/\n" +
        "wtx1aNkIv7db0uQ0NnRfvME8vG2PWbty36CNAYr/M5UVzUmELH2sGTyf2fKfNIUK\n" +
        "Iya/NRxCqxLAc34NYH0YyGJ9VcDjbEMNSBAHIqDdBNqKUPnjn454yoivU2oEs294\n" +
        "cGePMx3QLyPepMwUss8nW74yIF7vxfJ+KFDBGWNuZDRfXScsGIoeM0Vt9B+4fmnV\n" +
        "nP4Dw6l3IwmQH4ppjg08qTKvyrXcF2dPDWa98Xw6bA5G085Z/b/6/6GpkvKx/q6i\n" +
        "UqKwF7q5hkDcB+N4/5SgggN+MIIDejCCA3YwggJeoAMCAQICAWQwDQYJKoZIhvcN\n" +
        "AQELBQAwLTEUMBIGA1UEChMLU29tZUNvbXBhbnkxFTATBgNVBAMTDFJvb3QgQ0Eg\n" +
        "Q2VydDAeFw0xNjA4MDcyMTM3MTlaFw0xODA4MDcyMTM3MTlaMDUxFDASBgNVBAoT\n" +
        "C1NvbWVDb21wYW55MR0wGwYDVQQDExRJbnRlcm1lZGlhdGUgQ0EgQ2VydDCCASIw\n" +
        "DQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAJyUeQpxOxipUI4oBMUieoR7t9Np\n" +
        "nND3AOkw4LCIrT2A/6/EQWwbhOdTkOyD+wF5nz1Fd
        "YVT7T3C9AVVXrwkjnUzFWt4leM0aWgulMtqLlBZ2nypw7ZW4noIMhE1KDYFTSWAP\n" +
        "S9FzYbUpxDaCxwuD6tL/DrDbbDlhUTYjEzgOPTTQwcPOVoD8hQMY6juM9CdM8XVq\n" +
        "t1uK0jAfMa5Lpb9kb49xnH2CSv2m/FdSCejzFgqbowEmNwc72D5zewSrgY21iT4G\n" +
        "XT2H19XMXhvNLtLTrjH1lojTjUjEgO5vct9OEANICqpaxRZ/VOIVPPZL9SMCAwEA\n" +
        "AaOBmDCBlTAPBgNVHRMBAf8EBTADAQH/MDIGCCsGAQUFBwEBBCYwJDAiBggrBgEF\n" +
        "BQcwAYYWaHR0cDovL2xvY2FsaG9zdDozOTEzNDAfBgNVHSMEGDAWgBSTTLHoxMiV\n" +
        "AfbYlsnFXM76YjFUGTAdBgNVHQ4EFgQUYT525lwHCI4CmuWs8a7poaeKRJ4wDgYD\n" +
        "VR0PAQH/BAQDAgGGMA0GCSqGSIb3DQEBCwUAA4IBAQBOJzhXVvToqz50b8YoUF2C\n" +
        "/Qly7hkM+ba9F4DfMEmOlJ/W+gkYiu6G4CBoplXYb1AtanUXMCaoDnydv40ef+UK\n" +
        "NE1afsMOHRzXVKI+97WoCe407tGTM+WXadLsHeEQ/7Mr03lXGz3N5uGsLYBL0YM6\n" +
        "QzHEnij2ViJ5aO3256MCkgFAHzvHXtHA5MRAZMzyMxjbtuenHS2TqjivcCyfZkNM\n" +
        "yoIv9jbJXTXNqZf+o5BqQOri+ZSbJn6PbA7rFjR1dWIIKow0NfYa3+NFbrgvRhZf\n" +
        "6gFt5mqzLhM2PzYilVMbctatbOPEjoOS6BDww5G8oDreyAi9x2BtVbn4QtUNmGPX";

    private static final String INT_CA_OCSP_RESP =
        "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\n" +
        "X1ag

    private static final Date EVAL_DATE = new Date(1473199941000L);

    private static final Base64.Decoder DECODER = Base64.getMimeDecoder();

    public static void main(String[] args) throws Exception {
        TrustAnchor anchor;
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate rootCert = generateCertificate(cf, ROOT_CA_CERT);
        X509Certificate eeCert = generateCertificate(cf, EE_CERT);
        X509Certificate intCaCert = generateCertificate(cf, INT_CA_CERT);
        List<X509Certificate> certList = new ArrayList<X509Certificate>() {{
            add(eeCert);
            add(intCaCert);
        }};

        System.out.println("==== Certificate Path =====");
        for (X509Certificate c : certList) {
            System.out.println(c + "\n");
        }
        System.out.println("===========================");

        System.out.println("===== Test 1: TA(X509Certificate) =====");
        anchor = new TrustAnchor(rootCert, null);
        runTest(cf, certList, anchor);

        System.out.println("===== Test 2: TA(X500Principal, PublicKey =====");
        anchor = new TrustAnchor(rootCert.getSubjectX500Principal(),
                rootCert.getPublicKey(), null);
        runTest(cf, certList, anchor);

        System.out.println("===== Test 3: TA(String, PublicKey =====");
        anchor = new TrustAnchor(rootCert.getSubjectX500Principal().getName(),
                rootCert.getPublicKey(), null);
        runTest(cf, certList, anchor);
    }

    private static void runTest(CertificateFactory cf,
            List<X509Certificate> certList, TrustAnchor anchor)
            throws Exception {
        CertPath path = cf.generateCertPath(certList);
        CertPathValidator validator = CertPathValidator.getInstance("PKIX");

        System.out.println(anchor);

        PKIXRevocationChecker pkrev =
                (PKIXRevocationChecker)validator.getRevocationChecker();
        Map<X509Certificate, byte[]> responseMap = new HashMap<>();
        responseMap.put(certList.get(0), DECODER.decode(EE_OCSP_RESP));
        responseMap.put(certList.get(1), DECODER.decode(INT_CA_OCSP_RESP));
        pkrev.setOcspResponses(responseMap);
        PKIXParameters params =
                new PKIXParameters(Collections.singleton(anchor));
        params.addCertPathChecker(pkrev);
        params.setDate(EVAL_DATE);

        validator.validate(path, params);
    }

    private static X509Certificate generateCertificate(CertificateFactory cf,
            String encoded) throws CertificateException {
        ByteArrayInputStream is = new ByteArrayInputStream(encoded.getBytes());
        return (X509Certificate)cf.generateCertificate(is);
    }
}