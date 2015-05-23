/*
 * Copyright 2013-2015 Alvaro Sanchez-Mariscal <alvaro.sanchezmariscal@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package grails.plugin.springsecurity.rest

import com.nimbusds.jwt.JWT
import grails.plugin.springsecurity.rest.token.AccessToken
import grails.plugin.springsecurity.rest.token.generation.jwt.AbstractJwtTokenGenerator
import grails.plugin.springsecurity.rest.token.generation.jwt.DefaultRSAKeyProvider
import grails.plugin.springsecurity.rest.token.generation.jwt.EncryptedJwtTokenGenerator
import grails.plugin.springsecurity.rest.token.generation.jwt.SignedJwtTokenGenerator
import grails.plugin.springsecurity.rest.token.storage.jwt.JwtTokenStorageService
import grails.spring.BeanBuilder
import groovyx.gbench.BenchmarkBuilder
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import spock.lang.Specification

class JwtServiceSpec extends Specification {

    void "it can serialize and deserialize compressed objects"() {
        given:
        UserDetails userDetails = new User('username', 'password', [new SimpleGrantedAuthority('ROLE_USER')])

        when:
        String serialized = JwtService.serialize(userDetails)
        UserDetails deserialized = JwtService.deserialize(serialized)

        then:
        userDetails == deserialized
    }

    void "performance test of JWT parsing"() {
        given:
        UserDetails userDetails = new User('username', 'password', [new SimpleGrantedAuthority('ROLE_USER')])

        SignedJwtTokenGenerator signedJwtTokenGenerator = getTokenGenerator(false)
        AccessToken signedAccessToken = signedJwtTokenGenerator.generateAccessToken(userDetails)

        EncryptedJwtTokenGenerator encryptedJwtTokenGenerator = getTokenGenerator(true)
        AccessToken encryptedAccessToken = encryptedJwtTokenGenerator.generateAccessToken(userDetails)

        when:
        JWT jwt
        def bm = new BenchmarkBuilder().run(verbose: true) {
            'SignedJwt' {
                jwt = signedJwtTokenGenerator.jwtTokenStorageService.jwtService.parse(signedAccessToken.accessToken)
            }
            'EncryptedJwt' {
                jwt = encryptedJwtTokenGenerator.jwtTokenStorageService.jwtService.parse(encryptedAccessToken.accessToken)
            }
        }
        bm.prettyPrint()
        println "Done"

        then:
        jwt

    }

    private AbstractJwtTokenGenerator getTokenGenerator(boolean useEncryptedJwt) {
        BeanBuilder beanBuilder = new BeanBuilder()
        beanBuilder.beans {
            keyProvider(DefaultRSAKeyProvider)

            jwtService(JwtService) {
                keyProvider = ref('keyProvider')
                jwtSecret = 'foo123'*8
            }
            tokenStorageService(JwtTokenStorageService) {
                jwtService = ref('jwtService')
            }

            if (useEncryptedJwt) {
                tokenGenerator(EncryptedJwtTokenGenerator) {
                    jwtTokenStorageService = ref('tokenStorageService')
                    keyProvider = ref('keyProvider')
                    defaultExpiration = 3600
                }
            } else {
                tokenGenerator(SignedJwtTokenGenerator) {
                    jwtTokenStorageService = ref('tokenStorageService')
                    jwtSecret = 'foo123'*8
                    defaultExpiration = 3600
                }
            }
        }

        return beanBuilder.createApplicationContext().getBean('tokenGenerator')
    }

}
