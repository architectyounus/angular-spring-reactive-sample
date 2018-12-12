package com.example.demo

import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.support.beans
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.http.HttpMethod
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.ServerResponse.*
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.net.URI
import java.time.LocalDateTime

@SpringBootApplication
class DemoApplication


fun beans() = beans {
    bean {
        CommandLineRunner {
            println("start data initialization...")
            val posts = ref<PostRepository>();

            Flux.concat(
                    posts.deleteAll(),
                    posts.saveAll(
                            arrayListOf(
                                    Post(null, "my first post", "content of my first post"),
                                    Post(null, "my second post", "content of my second post")
                            )
                    )
            )
//            posts.deleteAll()
//                    .thenMany<Post> {
//                        posts.saveAll(
//                                arrayListOf(
//                                        Post(null, "my first post", "content of my first post"),
//                                        Post(null, "my second post", "content of my second post")
//                                )
//                        )
//                    }
                    .log()
                    .subscribe(null, null, { println("data initialization done.") })
        }
    }
    bean {
        PostRoutes(PostHandler(ref())).routes()
    }

    bean<PasswordEncoder> {
        PasswordEncoderFactories.createDelegatingPasswordEncoder()
    }

    bean<SecurityWebFilterChain> {
        ref<ServerHttpSecurity>().authorizeExchange()
                .pathMatchers(HttpMethod.GET, "/posts/**").permitAll()
                .pathMatchers(HttpMethod.DELETE, "/posts/**").hasRole("ADMIN")
                .pathMatchers("/posts/**").authenticated()
                //.pathMatchers("/users/{user}/**").access(this::currentUserMatchesPath)
                .anyExchange().permitAll()
                .and()
                .csrf().disable()
                .build()
    }

    bean {
        val passwordEncoder = ref<PasswordEncoder>()
        val user = User.withUsername("user")
                .passwordEncoder { it -> passwordEncoder.encode(it) }
                .password("password")
                .roles("USER").build()
        val admin = User.withUsername("admin")
                .password("password")
                .passwordEncoder { it -> passwordEncoder.encode(it) }
                .roles("USER", "ADMIN")
                .build()
        MapReactiveUserDetailsService(user, admin)
    }

}

fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args) {
        addInitializers(beans())
    }
}

class PostRoutes(private val postHandler: PostHandler) {
    fun routes() = router {
        "/posts".nest {
            GET("", postHandler::all)
            GET("/{id}", postHandler::get)
            POST("", postHandler::create)
            PUT("{id}", postHandler::update)
            DELETE("/{id}", postHandler::delete)
        }

    }
}

class PostHandler(private val posts: PostRepository) {


    fun all(req: ServerRequest): Mono<ServerResponse> {
        return ok().body(this.posts.findAll(), Post::class.java)
    }

    fun create(req: ServerRequest): Mono<ServerResponse> {
        return req.bodyToMono(Post::class.java)
                .flatMap { this.posts.save(it) }
                .flatMap { created(URI.create("/posts/" + it.id)).build() }
    }

    fun get(req: ServerRequest): Mono<ServerResponse> {
        return this.posts.findById(req.pathVariable("id"))
                .flatMap { ok().body(Mono.just(it), Post::class.java) }
                .switchIfEmpty(notFound().build())
    }

    fun update(req: ServerRequest): Mono<ServerResponse> {
        return this.posts.findById(req.pathVariable("id"))
                .zipWith(req.bodyToMono(Post::class.java))
                .map { it.t1.copy(title = it.t2.title, content = it.t2.content) }
                .flatMap { this.posts.save(it) }
                .flatMap { noContent().build() }
    }

    fun delete(req: ServerRequest): Mono<ServerResponse> {
        return this.posts.deleteById(req.pathVariable("id"))
                .flatMap { noContent().build() }
    }
}

@Document
data class Post(
        @Id var id: String? = null,
        var title: String? = null,
        var content: String? = null,
        @CreatedDate var createdDate: LocalDateTime = LocalDateTime.now()
)

interface PostRepository : ReactiveMongoRepository<Post, String>
