package com.yakub.themoviedbsample.data.repository;

import android.support.annotation.VisibleForTesting;

import com.yakub.themoviedbsample.data.model.Movie;

import org.reactivestreams.Publisher;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import io.reactivex.Flowable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;

public class MoviesRepository implements MoviesDataSource {

  private MoviesDataSource remoteDataSource;
  private MoviesDataSource localDataSource;

  @VisibleForTesting List<Movie> caches;

  @Inject public MoviesRepository(@Local MoviesDataSource localDataSource,
                                  @Remote MoviesDataSource remoteDataSource) {
    this.localDataSource = localDataSource;
    this.remoteDataSource = remoteDataSource;

    caches = new ArrayList<>();
  }

  @Override public Flowable<List<Movie>> loadPopularMovies(boolean forceRemote, int page ) {
    if (forceRemote) {
      return refreshPopularMoviesData(page);
    } else {
      if (caches.size() > 0) {
        // if cache is available, return it immediately
        return Flowable.just(caches);
      } else {
        // else return data from local storage
        return localDataSource.loadPopularMovies(false, page)
            .take(1)
            .flatMap(Flowable::fromIterable)
            .doOnNext(question -> caches.add(question))
            .toList()
            .toFlowable()
            .filter(list -> !list.isEmpty())
            .switchIfEmpty(
                refreshPopularMoviesData(page)); // If local data is empty, fetch from remote source instead.
      }
    }
  }

  @Override public Flowable<List<Movie>> loadTopRatedMovies(boolean forceRemote) {
    if (forceRemote) {
      return refreshTopRatedMoviesData();
    } else {
      if (caches.size() > 0) {
        // if cache is available, return it immediately
        return Flowable.just(caches);
      } else {
        // else return data from local storage
        return localDataSource.loadTopRatedMovies(false)
                .take(1)
                .flatMap(Flowable::fromIterable)
                .doOnNext(question -> caches.add(question))
                .toList()
                .toFlowable()
                .filter(list -> !list.isEmpty())
                .switchIfEmpty(
                        refreshTopRatedMoviesData()); // If local data is empty, fetch from remote source instead.
      }
    }
  }

  @Override public Flowable<List<Movie>> searchMovie(boolean forceRemote, String queryText) {
    if (forceRemote) {
      return refreshSearchMovieData(queryText);
    } else {
      if (caches.size() > 0) {
        // if cache is available, return it immediately
        return Flowable.just(caches);
      } else {
        // else return data from local storage
        return localDataSource.searchMovie(false, queryText)
                .take(1)
                .flatMap(Flowable::fromIterable)
                .doOnNext(question -> caches.add(question))
                .toList()
                .toFlowable()
                .filter(list -> !list.isEmpty())
                .switchIfEmpty(
                        refreshSearchMovieData(queryText)); // If local data is empty, fetch from remote source instead.
      }
    }
  }

  /**
   * Fetches data from remote source.
   * Save it into both local database and cache.
   *
   * @return the Flowable of newly fetched data.
   */
  Flowable<List<Movie>> refreshPopularMoviesData(int page) {
    return remoteDataSource.loadPopularMovies(true, page).doOnNext(list -> {
      // Clear cache
      caches.clear();
      // Clear data in local storage
      localDataSource.clearData();
    }).flatMap(Flowable::fromIterable).doOnNext(question -> {
      caches.add(question);
      localDataSource.addMovie(question);
    }).toList().toFlowable();
  }

  /**
   * Fetches data from remote source.
   * Save it into both local database and cache.
   *
   * @return the Flowable of newly fetched data.
   */
  Flowable<List<Movie>> refreshSearchMovieData(String queryText) {
    return remoteDataSource.searchMovie(true, queryText).doOnNext(list -> {
      // Clear cache
      caches.clear();
      // Clear data in local storage
      localDataSource.clearData();
    }).flatMap(Flowable::fromIterable).doOnNext(question -> {
      caches.add(question);
      localDataSource.addMovie(question);
    }).toList().toFlowable();
  }

  /**
   * Fetches data from remote source.
   * Save it into both local database and cache.
   *
   * @return the Flowable of newly fetched data.
   */
  Flowable<List<Movie>> refreshTopRatedMoviesData() {
    return remoteDataSource.loadTopRatedMovies(true).doOnNext(list -> {
      // Clear cache
      caches.clear();
      // Clear data in local storage
      localDataSource.clearData();
    }).flatMap(Flowable::fromIterable).doOnNext(question -> {
      caches.add(question);
      localDataSource.addMovie(question);
    }).toList().toFlowable();
  }

  /**
   * Loads a question by its question id.
   *
   *
   * @param onlineRequired
   * @param movieId question's id.
   * @return a corresponding question from cache.
   */
  public Flowable<Movie> getMovie(boolean onlineRequired, long movieId) {
//    return Flowable.fromIterable(caches).filter(movie -> movie.getId() == movieId);

    if (onlineRequired) {
      return getMovie(movieId);
    } else {
        // else return data from local storage
        return localDataSource.getMovie(onlineRequired, movieId)
                .filter(o -> o!=null)
                .switchIfEmpty(
                        getMovie(movieId)); // If local data is empty, fetch from remote source instead.

    }
  }

  Flowable<Movie> getMovie(long movieId) {
    return remoteDataSource.getMovie(true, movieId)
            .doOnError(new Consumer<Throwable>() {
              @Override
              public void accept(Throwable throwable) throws Exception {
                Flowable.just(throwable);
              }
            })
              .flatMap(new Function<Movie, Publisher<Movie>>() {
              @Override
              public Publisher<Movie> apply(Movie movie) throws Exception {
                localDataSource.addMovie(movie);
                return  Flowable.just(movie);
              }
            });
  }

  @Override public void addMovie(Movie question) {
    //Currently, we do not need this.
    throw new UnsupportedOperationException("Unsupported operation");
  }

  @Override public void clearData() {
    caches.clear();
    localDataSource.clearData();
  }
}
