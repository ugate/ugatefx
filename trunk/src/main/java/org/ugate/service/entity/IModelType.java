package org.ugate.service.entity;

/**
 * {@linkplain Model} type descriptor
 */
public interface IModelType<T> {

	/**
	 * @return The key to a model field
	 */
	public String getKey();

	/**
	 * @return The flag that indicates if the model field is transferable to
	 *         remote nodes
	 */
	public boolean canRemote();
}