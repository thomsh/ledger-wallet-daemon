package co.ledger.wallet.daemon.utils

import scala.collection.mutable

object Utils {

  import com.twitter.util.{Return, Throw, Future => TwitterFuture, Promise => TwitterPromise}

  import scala.collection.JavaConverters._
  import scala.concurrent.{ExecutionContext, Future => ScalaFuture, Promise => ScalaPromise}
  import scala.util.{Failure, Success}

  /** Convert from a Twitter Future to a Scala Future */
  implicit class RichTwitterFuture[A](val tf: TwitterFuture[A]) extends AnyVal {
    def asScala(): ScalaFuture[A] = {
      val promise: ScalaPromise[A] = ScalaPromise()
      tf.respond {
        case Return(value) => promise.success(value)
        case Throw(exception) => promise.failure(exception)
      }
      promise.future
    }
  }

  /** Convert from a Scala Future to a Twitter Future */
  implicit class RichScalaFuture[A](val sf: ScalaFuture[A]) extends AnyVal {
    def asTwitter()(implicit e: ExecutionContext): TwitterFuture[A] = {
      val promise: TwitterPromise[A] = new TwitterPromise[A]()
      sf.onComplete {
        case Success(value) => promise.setValue(value)
        case Failure(exception) => promise.setException(exception)
      }
      promise
    }
  }

  implicit class AsArrayList[T](val input: Seq[T]) extends AnyVal {
    def asArrayList : java.util.ArrayList[T] = new java.util.ArrayList[T](input.asJava)
  }

  def newConcurrentSet[T]: mutable.Set[T] = {
    java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap[T, java.lang.Boolean]()).asScala
  }

  implicit class RichBigInt(val i: co.ledger.core.BigInt) extends AnyVal {
    def asScala: BigInt = BigInt(i.toString(10))
  }

}
