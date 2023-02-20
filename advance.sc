#!/usr/local/bin/env -S scala-cli shebang --quiet

// to advance all projects:
//   % ./advance.sc scalacheck
// to advance just selected ones:
//   % ./advance.sc scalacheck scalatest specs2

//> using scala "3.2.2"
//> using option "-source:future"
//> using lib "org.scala-lang.modules::scala-parallel-collections:1.0.4"
//> using lib "com.lihaoyi::os-lib:0.9.0"

import scala.collection.parallel.CollectionConverters.* // for .par

def munge(l: String, replacement: String): String =
  if (l.startsWith("""  uri: "https://github"""))
  then s"""  uri: "$replacement""""
  else l

def getSha(repo: String, ref: String): String =
  if (ref.matches("\\p{XDigit}{40}"))
  then ref
  else
    import scala.sys.process.*
    val cmd = s"git ls-remote $repo $ref"
    def matches(s: String) =
      s.containsSlice("refs/heads/") || s.containsSlice("refs/tags/")
    Process(cmd).lazyLines.find(matches) match
      case Some(line) =>
        line.split("\\s").head
      case None =>
        throw new RuntimeException(s"$repo $ref")

// regexes for matching first line of each project's config
// note that we require an explicit tag or branch name,
// or a SHA (which must be the whole 40 characters).
// (dbuild defaults to `master`, but I prefer to make it explicit,
// to avoid confusion over whether the default is `master` or
// the repo's own default branch, which might be called something else)
object Regexes:
  // I would prefer to just have these as top-level `val`s but the script
  // deadlocks if I do that. some problem with parallel collections and
  // script wrappers, I guess. I didn't investigate
  val GitHub = """// (https://github.com/\S*.git)#(\S*)(\s*.*)""".r
  val Ivy = """// ivy:.*""".r
import Regexes.*

def filesIn(dir: String): Seq[os.Path] =
  os.list(os.pwd / dir).filter(_.ext == "conf").toSeq
val allFiles = filesIn("core") ++ filesIn("proj")
val selectedFiles =
  if args.isEmpty then
    allFiles
  else
    allFiles.filter(file => args.contains(file.baseName))

if selectedFiles.isEmpty then
  println("no matches")
  sys.exit(1)

for file <- selectedFiles.par
do
  val lines = os.read.lines(file).to(Vector)
  lines.head match
    case GitHub(repo, ref, comment) =>
      val uri = s"$repo#${getSha(repo, ref)}"
      println(uri) // indicate progress
      os.write.over(file,
        lines.map(munge(_, uri)).mkString("", "\n", "\n"))
    case bad =>
      throw new IllegalArgumentException(bad)
