# lambda-days-free-workshop

## Purpose

The purpose of this project is to demonstrate how a REST app can be written in a completely pure, functional manner. It also serves as a playground for various ideas: tagged types, WithId, JWT, akka-http directives. Feel free to collaborate

## Ideas

* `tagged types` as a replacement for case-class wrappers
    * Implementation [tag.scala](https://github.com/theiterators/lambda-days-free-workshop/blob/master/src/main/scala/pl/iterators/forum/utils/tag.scala).
    * Usage in [domain](https://github.com/theiterators/lambda-days-free-workshop/blob/master/src/main/scala/pl/iterators/forum/domain/package.scala)
    * See how covariance can be of importance eg. in `Id` tag
    * Tagging happens at system edges see [CommonJsonProtocol.scala](https://github.com/theiterators/lambda-days-free-workshop/blob/master/src/main/scala/pl/iterators/forum/resources/CommonJsonProtocol.scala) and 
    [TypeMappers.scala](https://github.com/theiterators/lambda-days-free-workshop/blob/master/src/main/scala/pl/iterators/forum/repositories/interpreters/TypeMappers.scala)

* `WithId` type to help with moving database ids out of domain models
    * Implementation [WithId.scala](https://github.com/theiterators/lambda-days-free-workshop/blob/master/src/main/scala/pl/iterators/forum/utils/db/WithId.scala)
    * In Slick tables [TableWithId.scala](https://github.com/theiterators/lambda-days-free-workshop/blob/master/src/main/scala/pl/iterators/forum/utils/db/TableWithId.scala)
    and repositories [Repository.scala](https://github.com/theiterators/lambda-days-free-workshop/blob/master/src/main/scala/pl/iterators/forum/utils/db/Repository.scala)
    
* and, of course, `Free` from `cats` to glue everything together
    * Some syntax helpers [syntax.scala](https://github.com/theiterators/lambda-days-free-workshop/blob/master/src/main/scala/pl/iterators/forum/utils/free/syntax.scala)
    * Services as free programs [services package](https://gitlab.iterato.rs/mrzeznicki/Forum/tree/de6f1131003e0f1163daf104a0d89cd4ec803834/src/main/scala/pl/iterators/forum/services)
    * Repositories as interpreters [repositories package](https://gitlab.iterato.rs/mrzeznicki/Forum/tree/de6f1131003e0f1163daf104a0d89cd4ec803834/src/main/scala/pl/iterators/forum/repositories)
    
* Smaller things like:
    * JWT support [JwtSupport.scala](https://github.com/theiterators/lambda-days-free-workshop/blob/master/src/main/scala/pl/iterators/forum/resources/JwtSupport.scala)
    * i18n using Rapture [i18n package](https://gitlab.iterato.rs/mrzeznicki/Forum/tree/de6f1131003e0f1163daf104a0d89cd4ec803834/src/main/scala/pl/iterators/forum/domain/i18n)
    * akka-http helpers [Resource.scala](https://github.com/theiterators/lambda-days-free-workshop/blob/master/src/main/scala/pl/iterators/forum/resources/Resource.scala)
    * Algebraic domain modelling eg. [Account.scala](https://github.com/theiterators/lambda-days-free-workshop/blob/master/src/main/scala/pl/iterators/forum/domain/Account.scala)
    or [Post.scala](https://github.com/theiterators/lambda-days-free-workshop/blob/master/src/main/scala/pl/iterators/forum/domain/Post.scala)
    * Using rollbacks and fixtures to easily test db [Base.scala](https://github.com/theiterators/lambda-days-free-workshop/blob/master/src/it/scala/pl/iterators/forum/fixtures/Base.scala)
    and [Accounts.scala](https://github.com/theiterators/lambda-days-free-workshop/blob/master/src/it/scala/pl/iterators/forum/fixtures/Accounts.scala)
    
## Architecture

Architecture is based on ideas from `Functional and Reactive Domain Modelling`. It retains 3-layered approach: there are services, resources and repositories. The meaning of key components differs from what you'd expect from non-Free program, though.

### Service

Service constructs a `Free` program consisting of ADTs found in repositories. Thus, you can say, that repository formulates a language, which any services can use to build a complete functionality.
Service is normally a trait which imports repositories for ADTs and domain objects. Services are completely pure and testable. For example, to store a post:

```scala
  def newPost(threadId: ThreadId, authorId: AuthorId, postContent: PostContent): PostOperation[Either[PostError, FullPostWithId]] =
    lookupThread(threadId)
      .toEither(ifNone = NoSuchThread)
      .subflatMap(thread => Either.cond(test = !thread.isClosed, thread, ThreadClosed))
      .flatMapF(thread => PostRepository.storePost(thread.id, authorId, postContent))
      .value

  def lookupThread(id: ThreadId)                    = PostRepository.lookupThread(id)

```


Obviously - secret of testability lies in services' independence of actual interpretation and ability to swap interpreters

More sophisticated examples include configurable operations eg.

```scala
  def passwordPolicy: PasswordPlain => Either[PasswordTooWeak.type, String]
  def confirmationTokenTtl: Duration

  def update(id: AccountId, accountChangeRequest: AccountChangeRequest): AccountOperation[StoreResult] =
    (for {
      _       <- EitherT.fromEither[AccountOperation](accountChangeRequest.validatePassword(passwordPolicy))
      updated <- EitherT(AccountRepository.update(id, accountChangeRequest.updateFunction))
    } yield updated).value

  def confirm(accountConfirmRequest: AccountConfirmRequest): ConfirmationTokenWithAccountOperation[Either[ConfirmationError, Ok.type]] =
    ConfirmationTokenOrAccount
      .queryToken(accountConfirmRequest.email, accountConfirmRequest.confirmationToken)
      .toEither(ifNone = InvalidToken)
      .subflatMap(confirmationToken =>
        Either.cond(test = !confirmationToken.isExpired(confirmationTokenTtl), accountConfirmRequest.email, TokenExpired))
      .flatMapF(email => ConfirmationTokenOrAccount.setConfirmed(email, accountConfirmRequest.nick))
      .value

```

Tests can fake anything

```scala
  val accountService = new AccountService {
    private val passwordMinLength = 4

    override val passwordPolicy = (password: PasswordPlain) =>
      for {
        _ <- mustBeOfLength(passwordMinLength)(password)
        _ <- mustContainLetter(password)
        _ <- mustContainUpper(password)
      } yield password

    override val confirmationTokenTtl = Duration.ofMinutes(1)
    override val messages = new Messages {
      override val from = EmailAddress("noreply@example.com".@@[Email], name = Some("no-reply"))
    }

  }

```

Also, you can test things that happen _out-of-band_ like sending emails plus its complex interactions with generating tokens:

```scala
 type EmailLog[V] = Writer[List[EmailMessage], V]

  protected def writeEmailLogValue: Id ~> EmailLog = λ[Id ~> EmailLog](Writer.value(_))
  def mailingLogger: MailingRepository ~> EmailLog =
    λ[MailingRepository ~> EmailLog] {
      case SendEmail(message) => Writer(List(message), Ok)
    }
  val confirmationTokenOrEmailLoggingOrAccountInterpreter
    : ConfirmationTokenOrEmailOrAccount ~> EmailLog = (accountInterpreter andThen writeEmailLogValue) or (mailingLogger or (tokenInterpreter andThen writeEmailLogValue))
    
  it("should send confirmation email") {
     new AccountFixture with ConfirmationTokenFixture with MailingFixture {
        import cats.instances.list._

        val log = createF(AccountCreateRequest("user@example.com".@@[Email], "Dr56::sf".@@[Password]))(confirmationEmailEnv).value
          .foldMap(confirmationTokenOrEmailLoggingOrAccountInterpreter)

        val emails = log.written
        emails should have length 1
        
        val token = tokenInterpreter.find("user@example.com".@@[Email]).head

        emails.head should matchPattern {
          case accountService.messages.ConfirmationMessage(email, _, link)
              if email == "user@example.com" && link == confirmationEmailEnv.confirmationLink("user@example.com".@@[Email], token) =>
        }
     }
  }


```

### Repository

Repository is **not** a db-repository where you'll typically find queries or tables but rather collection of ADTs for describing interaction with storage. For instance

```scala
sealed trait MailingRepository[A]

object MailingRepository {
  case class SendEmail(message: EmailMessage) extends MailingRepository[Ok.type]

  def sendEmail(message: EmailMessage) = Free.liftF(SendEmail(message))
}

``` 

These are used by services to create complex scenarios. Eventually, you'll write some specific **interpreters** for these eg. backed by a db. But these are implementation details. Also, please note that Slick stuff is called repository too - but it is in utils [Repository.scala](https://github.com/theiterators/lambda-days-free-workshop/blob/master/src/main/scala/pl/iterators/forum/utils/db/Repository.scala).
As such, it is only **used** by a specific interpreter. So - in a nutshell what you treated as Slick repo is now broken down into two things:
collection of generic queries (found in utils) plus model and specific queries (as interpreter). 

Repositories are tested usually in `it:test` (because they involve some external system as SMTP server or a DB) 

### Resources

Resources are facing outside world via HTTP interface - sometimes you call them routers. They demand a service (for making a program) and an interpreter (for, well, interpreting it - usually to a `Future`)
and push the result to the caller via `akka-http`. Typical interaction:

```scala
trait PostsResource extends Resource with PostsResource.PostsProtocol {
  def postService: PostService
  def postRepositoryInterpreter: PostRepositoryInterpreter

  import PostsResource._
  import cats.instances.future._

  private def runCreateNewThread(authorId: AuthorId, newThreadRequest: NewThreadRequest) =
    postService.newThread(authorId, newThreadRequest) foldMap postRepositoryInterpreter
  protected lazy val createNewThread: Route = (post & extractClaims) { claims =>
    val authorId = claims.id.asAuthorId

    entity(as[NewThreadRequest]) { newThreadRequest =>
      withBaseUri(implicit uri => onSuccess(runCreateNewThread(authorId, newThreadRequest))(completeWithLocation))
    }
  }

  private def runLookupThread(threadId: ThreadId) =
    postService.lookupThread(threadId) foldMap postRepositoryInterpreter
  protected val lookupThread: ThreadId => Route = id =>
    get {
      jwtAuthorize() {
        onSuccess(runLookupThread(id)) {
          case None => complete(NotFound)
          case Some(thread) =>
            withBaseUri(implicit uri => completeAsResource(thread))
        }
      }
  }

  val threadsRoutes: Route = pathEndOrSingleSlash { createNewThread ~ fetchThreads } ~
    pathPrefix(LongNumber.map(ThreadId(_))) { threadId =>
      pathEnd { lookupThread(threadId) } ~
        pathPrefix("posts") {
          pathEnd { createNewPost(threadId) ~ fetchPosts(threadId) } ~
            path(LongNumber.map(PostId(_))) { postId =>
              lookupPost(threadId)(postId) ~ updatePost(threadId)(postId)
            }
        }
    }
}

```

They are also totally unit-testable. 
Sometimes they need to provide some more complex environment (for example, to send an email), or combine multiple interpreters together

```scala
trait AccountsResource extends Resource with AccountsResource.AccountsProtocol with LanguageSupport {
  def accountService: AccountService
  def accountRepositoryInterpreter: AccountRepositoryInterpreter
  def confirmationTokenInterpreter: ConfirmationTokenRepositoryInterpreter
  def mailingInterpreter: MailingRepositoryInterpreter

  def confirmationLinkTemplate: String

  import AccountsResource.{AccountsResourceObject, MailEnv}
  import cats.instances.future._

  private def withMailEnv = extractHost.flatMap { host =>
    determineLocale().flatMap { locale =>
      provide(new MailEnv(locale, host, confirmationLinkTemplate))
    }
  }

  private def runCreateAccount(accountCreateRequest: AccountCreateRequest)(mailEnv: ConfirmationEmailEnv) =
    accountService
      .createRegular(accountCreateRequest)(mailEnv)
      .value foldMap (accountRepositoryInterpreter or (mailingInterpreter or confirmationTokenInterpreter))
  protected val createAccount: Route = (post & entity(as[AccountCreateRequest])) { accountCreateRequest =>
    withMailEnv { mailEnv =>
      onSuccess(runCreateAccount(accountCreateRequest)(mailEnv)) {
        case Left(error)          => complete(Conflict -> error)
        case Right(accountWithId) => completeWithLocation(accountWithId)
      }
    }
  }
}
```



